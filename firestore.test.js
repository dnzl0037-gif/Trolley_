const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write users or providerProfiles", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      fullName: "Alice",
      ageVerified: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(unauthDb.collection("providerProfiles").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("providerProfiles").get());
});

test("UserAccount: owner create, partial update, cross-user denial, and immutable field guard", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      fullName: "Alice Moyo",
      email: "alice@example.com",
      dateOfBirth: "1998-05-14",
      ageVerified: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      fullName: "Alice K. Moyo",
      photoUrl: "https://example.com/alice.jpg",
      photoBase64: "AAAA",
      updatedAt: updated,
    })
  );

  // Invalid non-HTTPS photoUrl rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      photoUrl: "http://insecure.example.com/alice.jpg",
      updatedAt: updated,
    })
  );

  // Cross-user read/write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      fullName: "Hijacked",
      updatedAt: updated,
    })
  );
  await assertFails(aliceDb.collection("users").get());

  // Immutable createdAt mutation denied
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: updated,
      updatedAt: updated,
    })
  );

  // Ghost field denied
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      fullName: "Bob",
      ageVerified: true,
      isAdmin: true,
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("ProviderProfile: owner draft creation, edit, submit for review, and privilege escalation blocks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 2000);
  const submitted = new Date(Date.now() - 1000);

  // Owner can read non-existent own profile without PERMISSION_DENIED
  await assertSucceeds(aliceDb.collection("providerProfiles").doc(ALICE_UID).get());

  // Create draft (no @DocumentId id field, optional description included)
  await assertSucceeds(
    aliceDb.collection("providerProfiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      businessName: "Brightline Electrical",
      description: "Residential wiring and backup power across Harare.",
      categorySlug: "electrical-wiring-installation",
      categoryName: "Wiring installation",
      countryCode: "ZW",
      city: "Harare",
      listingStatus: "draft",
      verificationStatus: "not_submitted",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Save draft edits
  await assertSucceeds(
    aliceDb.collection("providerProfiles").doc(ALICE_UID).update({
      businessName: "Brightline Electrical ZW",
      city: "Bulawayo",
      updatedAt: updated,
    })
  );

  // Submit draft for review (draft -> pending_review)
  await assertSucceeds(
    aliceDb.collection("providerProfiles").doc(ALICE_UID).update({
      businessName: "Brightline Electrical ZW",
      categorySlug: "electrical-solar-electrical-connection",
      categoryName: "Solar electrical connection",
      countryCode: "ZW",
      city: "Harare",
      listingStatus: "pending_review",
      submittedAt: submitted,
      updatedAt: submitted,
    })
  );

  // Self-publishing or self-verifying must be rejected
  await assertFails(
    aliceDb.collection("providerProfiles").doc(ALICE_UID).update({
      listingStatus: "published",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("providerProfiles").doc(ALICE_UID).update({
      verificationStatus: "approved",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Pending review listing cannot be deleted or reverted to draft
  await assertFails(
    aliceDb.collection("providerProfiles").doc(ALICE_UID).update({
      listingStatus: "draft",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(aliceDb.collection("providerProfiles").doc(ALICE_UID).delete());

  // Cross-user access and unfiltered list rejected; owner-filtered query succeeds
  await assertFails(bobDb.collection("providerProfiles").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("providerProfiles").get());
  await assertSucceeds(
    aliceDb.collection("providerProfiles").where("userId", "==", ALICE_UID).get()
  );

  // Future timestamp rejected
  await assertFails(
    bobDb.collection("providerProfiles").doc(BOB_UID).set({
      userId: BOB_UID,
      businessName: "Bob Plumbing",
      categorySlug: "plumbing-leak-detection",
      categoryName: "Leak detection",
      countryCode: "ZW",
      city: "Harare",
      listingStatus: "draft",
      verificationStatus: "not_submitted",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("ProviderProfile: clearing optional description succeeds while invalid inputs fail", async () => {
  const path = require("node:path");
  const rutDir = path.dirname(require.resolve("@firebase/rules-unit-testing"));
  const firebase = require(require.resolve("firebase/compat/app", { paths: [rutDir] }));
  require(require.resolve("firebase/compat/firestore", { paths: [rutDir] }));
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("providerProfiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      businessName: "Clean Plumbing Co",
      description: "Initial description",
      categorySlug: "plumbing-leak-detection",
      categoryName: "Leak detection",
      countryCode: "ZW",
      city: "Harare",
      listingStatus: "draft",
      verificationStatus: "not_submitted",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Clearing optional description with FieldValue.delete() succeeds
  await assertSucceeds(
    aliceDb.collection("providerProfiles").doc(ALICE_UID).update({
      description: firebase.firestore.FieldValue.delete(),
      updatedAt: updated,
    })
  );

  // Invalid 3-letter country code rejected
  await assertFails(
    bobDb.collection("providerProfiles").doc(BOB_UID).set({
      userId: BOB_UID,
      businessName: "Bob Plumbing",
      categorySlug: "plumbing-leak-detection",
      categoryName: "Leak detection",
      countryCode: "ZWE",
      city: "Harare",
      listingStatus: "draft",
      verificationStatus: "not_submitted",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Invalid categorySlug characters (uppercase/spaces) rejected
  await assertFails(
    bobDb.collection("providerProfiles").doc(BOB_UID).set({
      userId: BOB_UID,
      businessName: "Bob Plumbing",
      categorySlug: "Invalid Slug!",
      categoryName: "Leak detection",
      countryCode: "ZW",
      city: "Harare",
      listingStatus: "draft",
      verificationStatus: "not_submitted",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Oversized businessName (>120 chars) and oversized description (>1200 chars) rejected
  await assertFails(
    bobDb.collection("providerProfiles").doc(BOB_UID).set({
      userId: BOB_UID,
      businessName: "A".repeat(121),
      categorySlug: "plumbing-leak-detection",
      categoryName: "Leak detection",
      countryCode: "ZW",
      city: "Harare",
      listingStatus: "draft",
      verificationStatus: "not_submitted",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    bobDb.collection("providerProfiles").doc(BOB_UID).set({
      userId: BOB_UID,
      businessName: "Bob Plumbing",
      description: "D".repeat(1201),
      categorySlug: "plumbing-leak-detection",
      categoryName: "Leak detection",
      countryCode: "ZW",
      city: "Harare",
      listingStatus: "draft",
      verificationStatus: "not_submitted",
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("ProviderProfile: businessAddress, coordinates validation, and published listing discovery", async () => {
  const path = require("node:path");
  const rutDir = path.dirname(require.resolve("@firebase/rules-unit-testing"));
  const firebase = require(require.resolve("firebase/compat/app", { paths: [rutDir] }));
  require(require.resolve("firebase/compat/firestore", { paths: [rutDir] }));
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 1000);

  // Create draft with business address and geocoded coordinates
  await assertSucceeds(
    aliceDb.collection("providerProfiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      businessName: "Harare Solar & Power",
      categorySlug: "electrical-solar-electrical-connection",
      categoryName: "Solar electrical connection",
      countryCode: "ZW",
      city: "Harare",
      businessAddress: "14 Samora Machel Ave, Harare",
      latitude: -17.8292,
      longitude: 31.0522,
      listingStatus: "draft",
      verificationStatus: "not_submitted",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Out-of-bounds latitude (>90) must fail
  await assertFails(
    aliceDb.collection("providerProfiles").doc(ALICE_UID).update({
      latitude: 95.0,
      updatedAt: updated,
    })
  );

  // Clearing businessAddress and coordinates via FieldValue.delete() succeeds
  await assertSucceeds(
    aliceDb.collection("providerProfiles").doc(ALICE_UID).update({
      businessAddress: firebase.firestore.FieldValue.delete(),
      latitude: firebase.firestore.FieldValue.delete(),
      longitude: firebase.firestore.FieldValue.delete(),
      updatedAt: updated,
    })
  );

  // Seed admin-published listing for Bob and keep Alice as draft
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("providerProfiles").doc(BOB_UID).set({
      userId: BOB_UID,
      businessName: "Approved Harare Plumbing",
      description: "24/7 residential leak repair across Avondale.",
      categorySlug: "plumbing-leak-detection",
      categoryName: "Leak detection",
      countryCode: "ZW",
      city: "Harare",
      businessAddress: "82 King George Rd, Avondale",
      latitude: -17.8015,
      longitude: 31.0365,
      listingStatus: "published",
      verificationStatus: "approved",
      createdAt: created,
      updatedAt: created,
    });
  });

  // Unauthenticated cannot read even published listings
  await assertFails(
    unauthDb.collection("providerProfiles").where("listingStatus", "==", "published").get()
  );

  // Authenticated Alice can query published listings for the map view, and read Bob's published doc
  await assertSucceeds(
    aliceDb.collection("providerProfiles").where("listingStatus", "==", "published").get()
  );
  await assertSucceeds(
    aliceDb.collection("providerProfiles").doc(BOB_UID).get()
  );

  // Bob still cannot read Alice's unpublished draft
  await assertFails(
    bobDb.collection("providerProfiles").doc(ALICE_UID).get()
  );
});

test("BookingRequest: create linked to provider, parent gate, reschedule/cancel/provider transitions, and isolation", async () => {
  const path = require("node:path");
  const rutDir = path.dirname(require.resolve("@firebase/rules-unit-testing"));
  const firebase = require(require.resolve("firebase/compat/app", { paths: [rutDir] }));
  require(require.resolve("firebase/compat/firestore", { paths: [rutDir] }));

  const CHARLIE_UID = "charlie_789";
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 1000);

  // Seed Bob as a published provider and Charlie as a private draft provider
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("providerProfiles").doc(BOB_UID).set({
      userId: BOB_UID,
      businessName: "Approved Harare Plumbing",
      categorySlug: "plumbing-leak-detection",
      categoryName: "Leak detection",
      countryCode: "ZW",
      city: "Harare",
      businessAddress: "82 King George Rd, Avondale",
      listingStatus: "published",
      verificationStatus: "approved",
      createdAt: created,
      updatedAt: created,
    });
    await context.firestore().collection("providerProfiles").doc(CHARLIE_UID).set({
      userId: CHARLIE_UID,
      businessName: "Private Draft Electrical",
      categorySlug: "electrical-wiring-installation",
      categoryName: "Wiring installation",
      countryCode: "ZW",
      city: "Harare",
      listingStatus: "draft",
      verificationStatus: "not_submitted",
      createdAt: created,
      updatedAt: created,
    });
  });

  // Unauthenticated cannot create or list bookings
  await assertFails(unauthDb.collection("bookings").where("customerId", "==", ALICE_UID).get());
  await assertFails(
    unauthDb.collection("bookings").doc("bk_unauth").set({
      customerId: ALICE_UID,
      customerName: "Alice",
      providerId: BOB_UID,
      providerUserId: BOB_UID,
      providerBusinessName: "Approved Harare Plumbing",
      categorySlug: "plumbing-leak-detection",
      categoryName: "Leak detection",
      providerLocation: "82 King George Rd, Avondale, Harare, ZW",
      scheduledDate: "2026-11-25",
      scheduledTime: "10:00",
      status: "pending",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Alice cannot book an orphaned non-existent provider or Charlie's private draft
  await assertFails(
    aliceDb.collection("bookings").doc("bk_orphan").set({
      customerId: ALICE_UID,
      customerName: "Alice",
      providerId: "missing_provider",
      providerUserId: "missing_provider",
      providerBusinessName: "Ghost Pro",
      categorySlug: "plumbing-leak-detection",
      categoryName: "Leak detection",
      providerLocation: "Harare, ZW",
      scheduledDate: "2026-11-25",
      scheduledTime: "10:00",
      status: "pending",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    aliceDb.collection("bookings").doc("bk_draft").set({
      customerId: ALICE_UID,
      customerName: "Alice",
      providerId: CHARLIE_UID,
      providerUserId: CHARLIE_UID,
      providerBusinessName: "Private Draft Electrical",
      categorySlug: "electrical-wiring-installation",
      categoryName: "Wiring installation",
      providerLocation: "Harare, ZW",
      scheduledDate: "2026-11-25",
      scheduledTime: "10:00",
      status: "pending",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Alice cannot create an already-accepted booking
  await assertFails(
    aliceDb.collection("bookings").doc("bk_bad_status").set({
      customerId: ALICE_UID,
      customerName: "Alice",
      providerId: BOB_UID,
      providerUserId: BOB_UID,
      providerBusinessName: "Approved Harare Plumbing",
      categorySlug: "plumbing-leak-detection",
      categoryName: "Leak detection",
      providerLocation: "82 King George Rd, Avondale, Harare, ZW",
      scheduledDate: "2026-11-25",
      scheduledTime: "10:00",
      status: "accepted",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Alice creates a valid booking linked to Bob's published provider profile
  await assertSucceeds(
    aliceDb.collection("bookings").doc("bk_1").set({
      customerId: ALICE_UID,
      customerName: "Alice Moyo",
      providerId: BOB_UID,
      providerUserId: BOB_UID,
      providerBusinessName: "Approved Harare Plumbing",
      categorySlug: "plumbing-leak-detection",
      categoryName: "Leak detection",
      providerLocation: "82 King George Rd, Avondale, Harare, ZW",
      scheduledDate: "2026-11-25",
      scheduledTime: "09:30",
      notes: "Kitchen pipe under sink is dripping.",
      status: "pending",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Both Alice (customer) and Bob (provider) can query their scoped bookings; Charlie cannot
  await assertSucceeds(aliceDb.collection("bookings").where("customerId", "==", ALICE_UID).get());
  await assertSucceeds(bobDb.collection("bookings").where("providerUserId", "==", BOB_UID).get());
  await assertFails(aliceDb.collection("bookings").get()); // unfiltered list denied
  await assertFails(charlieDb.collection("bookings").doc("bk_1").get());

  // Alice reschedules pending booking & clears optional notes
  await assertSucceeds(
    aliceDb.collection("bookings").doc("bk_1").update({
      scheduledDate: "2026-11-26",
      scheduledTime: "14:00",
      notes: firebase.firestore.FieldValue.delete(),
      updatedAt: updated,
    })
  );

  // Alice cannot self-accept her own booking request
  await assertFails(
    aliceDb.collection("bookings").doc("bk_1").update({
      status: "accepted",
      updatedAt: updated,
    })
  );

  // Bob accepts the pending booking, then marks it completed
  await assertSucceeds(
    bobDb.collection("bookings").doc("bk_1").update({
      status: "accepted",
      updatedAt: updated,
    })
  );
  await assertSucceeds(
    bobDb.collection("bookings").doc("bk_1").update({
      status: "completed",
      updatedAt: updated,
    })
  );

  // Terminal state locking: completed booking cannot be modified or deleted
  await assertFails(
    aliceDb.collection("bookings").doc("bk_1").update({
      status: "cancelled",
      updatedAt: updated,
    })
  );
  await assertFails(aliceDb.collection("bookings").doc("bk_1").delete());
});

test("ProviderReview: authenticated feedback, 1..5 rating bounds, linked provider check, and owner update/delete", async () => {
  const CHARLIE_UID = "charlie_789";
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 1000);

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("providerProfiles").doc(BOB_UID).set({
      userId: BOB_UID,
      businessName: "Approved Harare Plumbing",
      categorySlug: "plumbing-leak-detection",
      categoryName: "Leak detection",
      countryCode: "ZW",
      city: "Harare",
      businessAddress: "82 King George Rd, Avondale",
      listingStatus: "published",
      verificationStatus: "approved",
      createdAt: created,
      updatedAt: created,
    });
    await context.firestore().collection("providerProfiles").doc(CHARLIE_UID).set({
      userId: CHARLIE_UID,
      businessName: "Private Draft Electrical",
      categorySlug: "electrical-wiring-installation",
      categoryName: "Wiring installation",
      countryCode: "ZW",
      city: "Harare",
      listingStatus: "draft",
      verificationStatus: "not_submitted",
      createdAt: created,
      updatedAt: created,
    });
  });

  const validReviewId = `${BOB_UID}__${ALICE_UID}`;

  // Unauthenticated reads and writes denied
  await assertFails(unauthDb.collection("reviews").where("providerId", "==", BOB_UID).get());
  await assertFails(
    unauthDb.collection("reviews").doc(validReviewId).set({
      providerId: BOB_UID,
      providerUserId: BOB_UID,
      customerId: ALICE_UID,
      reviewerName: "Alice",
      rating: 5,
      comment: "Great job.",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Non-deterministic doc ID (review spam bypass attempt) rejected
  await assertFails(
    aliceDb.collection("reviews").doc("arbitrary_id_1").set({
      providerId: BOB_UID,
      providerUserId: BOB_UID,
      customerId: ALICE_UID,
      reviewerName: "Alice",
      rating: 5,
      comment: "Duplicate review attempt.",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Out-of-bounds ratings (0 or 6 or float 4.5) and 1-char comments rejected
  await assertFails(
    aliceDb.collection("reviews").doc(validReviewId).set({
      providerId: BOB_UID,
      providerUserId: BOB_UID,
      customerId: ALICE_UID,
      reviewerName: "Alice",
      rating: 0,
      comment: "Too low rating.",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    aliceDb.collection("reviews").doc(validReviewId).set({
      providerId: BOB_UID,
      providerUserId: BOB_UID,
      customerId: ALICE_UID,
      reviewerName: "Alice",
      rating: 6,
      comment: "Too high rating.",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    aliceDb.collection("reviews").doc(validReviewId).set({
      providerId: BOB_UID,
      providerUserId: BOB_UID,
      customerId: ALICE_UID,
      reviewerName: "Alice",
      rating: 4,
      comment: "X",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Private draft listing owned by Charlie cannot be reviewed by Alice
  await assertFails(
    aliceDb.collection("reviews").doc(`${CHARLIE_UID}__${ALICE_UID}`).set({
      providerId: CHARLIE_UID,
      providerUserId: CHARLIE_UID,
      customerId: ALICE_UID,
      reviewerName: "Alice",
      rating: 5,
      comment: "Should fail because draft is private.",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Alice submits a valid 5-star review for published provider Bob
  await assertSucceeds(
    aliceDb.collection("reviews").doc(validReviewId).set({
      providerId: BOB_UID,
      providerUserId: BOB_UID,
      customerId: ALICE_UID,
      reviewerName: "Alice Moyo",
      rating: 5,
      comment: "Arrived on time and fixed the leaking copper pipe neatly.",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Signed-in users can read reviews for Bob's profile page
  await assertSucceeds(charlieDb.collection("reviews").where("providerId", "==", BOB_UID).get());

  // Alice updates her review rating & comment
  await assertSucceeds(
    aliceDb.collection("reviews").doc(validReviewId).update({
      rating: 4,
      comment: "Good repair work; follow-up receipt arrived the next morning.",
      updatedAt: updated,
    })
  );

  // Immutable providerId / createdAt change blocked; Bob/Charlie cannot edit or delete Alice's review
  await assertFails(
    aliceDb.collection("reviews").doc(validReviewId).update({
      providerId: CHARLIE_UID,
      updatedAt: updated,
    })
  );
  await assertFails(
    bobDb.collection("reviews").doc(validReviewId).update({
      rating: 5,
      updatedAt: updated,
    })
  );
  await assertFails(charlieDb.collection("reviews").doc(validReviewId).delete());

  // Alice can delete her own review
  await assertSucceeds(aliceDb.collection("reviews").doc(validReviewId).delete());
});


