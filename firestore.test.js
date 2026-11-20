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

test("Hardcore Red-Team: self-review rating inflation and invalid ID/timestamp spoofing denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const futureTime = new Date(Date.now() + 120000);

  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("providerProfiles").doc(BOB_UID).set({
      userId: BOB_UID,
      businessName: "Approved Harare Plumbing",
      categorySlug: "plumbing-leak-detection",
      categoryName: "Leak detection",
      countryCode: "ZW",
      city: "Harare",
      listingStatus: "published",
      verificationStatus: "approved",
      createdAt: created,
      updatedAt: created,
    });
  });

  // Provider Bob cannot inflate his own star score by submitting a self-review
  await assertFails(
    bobDb.collection("reviews").doc(`${BOB_UID}__${BOB_UID}`).set({
      providerId: BOB_UID,
      providerUserId: BOB_UID,
      customerId: BOB_UID,
      reviewerName: "Bob",
      rating: 5,
      comment: "Self-inflated five star rating attempt.",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Path-traversal / non-regex document ID and future timestamps rejected
  await assertFails(
    aliceDb.collection("users").doc("alice_123").set({
      userId: ALICE_UID,
      fullName: "Alice",
      ageVerified: true,
      createdAt: futureTime,
      updatedAt: futureTime,
    })
  );
  await assertFails(
    aliceDb.collection("bookings").doc("bad.id!invalid").set({
      customerId: ALICE_UID,
      customerName: "Alice",
      providerId: BOB_UID,
      providerUserId: BOB_UID,
      providerBusinessName: "Approved Harare Plumbing",
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
});

test("ProviderSubscription: pricing matrix, free-tier limits, price tampering denial, and owner isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 1000);

  // Unauthenticated access denied
  await assertFails(unauthDb.collection("providerSubscriptions").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("providerSubscriptions").where("userId", "==", ALICE_UID).get());

  // Orphan subscription without a /providerProfiles/{uid} document fails
  await assertFails(
    aliceDb.collection("providerSubscriptions").doc(ALICE_UID).set({
      userId: ALICE_UID,
      providerId: ALICE_UID,
      businessName: "Alice Electrical",
      planTier: "free_starter",
      billingCycle: "monthly",
      paymentMethod: "none",
      currency: "USD",
      amountCents: 0,
      monthlyBookingLimit: 3,
      maxCategories: 1,
      subscriptionStatus: "active",
      autoRenew: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Create Alice's provider profile first
  await assertSucceeds(
    aliceDb.collection("providerProfiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      businessName: "Alice Electrical",
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

  // Price tampering attack: Business Elite annual at $0.00 or $1.00 instead of $83.92 (8392 cents) rejected
  await assertFails(
    aliceDb.collection("providerSubscriptions").doc(ALICE_UID).set({
      userId: ALICE_UID,
      providerId: ALICE_UID,
      businessName: "Alice Electrical",
      planTier: "business_elite",
      billingCycle: "yearly",
      paymentMethod: "ecocash",
      currency: "USD",
      amountCents: 100,
      monthlyBookingLimit: 9999,
      maxCategories: 10,
      subscriptionStatus: "active",
      autoRenew: true,
      maskedInstrument: "EcoCash +263 77***1234",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Free-tier quota bypass attack (claiming 9999 bookings on free_starter) rejected
  await assertFails(
    aliceDb.collection("providerSubscriptions").doc(ALICE_UID).set({
      userId: ALICE_UID,
      providerId: ALICE_UID,
      businessName: "Alice Electrical",
      planTier: "free_starter",
      billingCycle: "monthly",
      paymentMethod: "none",
      currency: "USD",
      amountCents: 0,
      monthlyBookingLimit: 9999,
      maxCategories: 1,
      subscriptionStatus: "active",
      autoRenew: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid Free Starter subscription succeeds
  await assertSucceeds(
    aliceDb.collection("providerSubscriptions").doc(ALICE_UID).set({
      userId: ALICE_UID,
      providerId: ALICE_UID,
      businessName: "Alice Electrical",
      planTier: "free_starter",
      billingCycle: "monthly",
      paymentMethod: "none",
      currency: "USD",
      amountCents: 0,
      monthlyBookingLimit: 3,
      maxCategories: 1,
      subscriptionStatus: "active",
      autoRenew: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Bob cannot read or hijack Alice's subscription; unfiltered list denied
  await assertFails(bobDb.collection("providerSubscriptions").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("providerSubscriptions").get());
  await assertSucceeds(aliceDb.collection("providerSubscriptions").where("userId", "==", ALICE_UID).get());

  // Paid subscription upgrade WITHOUT a valid 2FA financialVerifications record must fail
  await assertFails(
    aliceDb.collection("providerSubscriptions").doc(ALICE_UID).update({
      planTier: "business_elite",
      billingCycle: "yearly",
      paymentMethod: "ecocash",
      amountCents: 8392,
      monthlyBookingLimit: 9999,
      maxCategories: 10,
      subscriptionStatus: "pending_confirmation",
      autoRenew: true,
      maskedInstrument: "EcoCash +263 77***4567",
      updatedAt: updated,
    })
  );

  // Create valid 2FA verification audit record for Alice
  const nowSec = Math.floor(Date.now() / 1000) - 5;
  await assertSucceeds(
    aliceDb.collection("financialVerifications").doc("ver_alice_1").set({
      userId: ALICE_UID,
      providerId: ALICE_UID,
      verificationMethod: "firebase_reauth_and_totp",
      reauthenticated: true,
      totpVerified: true,
      firebaseAuthTimeSeconds: nowSec,
      actionType: "activate_subscription",
      verifiedAt: updated,
    })
  );

  // Valid upgrade to Business Elite Yearly ($9.99 * 12 * 0.70 = $83.92 -> 8392 cents) via EcoCash succeeds with verificationId
  await assertSucceeds(
    aliceDb.collection("providerSubscriptions").doc(ALICE_UID).update({
      planTier: "business_elite",
      billingCycle: "yearly",
      paymentMethod: "ecocash",
      amountCents: 8392,
      monthlyBookingLimit: 9999,
      maxCategories: 10,
      subscriptionStatus: "pending_confirmation",
      autoRenew: true,
      maskedInstrument: "EcoCash +263 77***4567",
      verificationId: "ver_alice_1",
      updatedAt: updated,
    })
  );

  // Raw unmasked card/phone (>5 consecutive digits) rejected by PCI/PII safety rule
  await assertFails(
    aliceDb.collection("providerSubscriptions").doc(ALICE_UID).update({
      maskedInstrument: "Visa 4111111111111111",
      verificationId: "ver_alice_1",
      updatedAt: updated,
    })
  );

  // Delete denied
  await assertFails(aliceDb.collection("providerSubscriptions").doc(ALICE_UID).delete());
});

test("BillingPayment: PCI PAN/CVV ghost-field blocking, 30% discount math, 2FA verification link, and immutable ledger", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const created = new Date(Date.now() - 10000);
  const nowSec = Math.floor(Date.now() / 1000) - 5;

  await assertSucceeds(
    aliceDb.collection("providerProfiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      businessName: "Alice Electrical",
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

  await assertSucceeds(
    aliceDb.collection("financialVerifications").doc("ver_pay_1").set({
      userId: ALICE_UID,
      providerId: ALICE_UID,
      verificationMethod: "firebase_reauth_and_totp",
      reauthenticated: true,
      totpVerified: true,
      firebaseAuthTimeSeconds: nowSec,
      actionType: "activate_subscription",
      verifiedAt: created,
    })
  );

  // Paid payment receipt missing verificationId rejected
  await assertFails(
    aliceDb.collection("billingPayments").doc("pay_no_2fa").set({
      userId: ALICE_UID,
      providerId: ALICE_UID,
      planTier: "business_elite",
      billingCycle: "yearly",
      paymentMethod: "visa",
      currency: "USD",
      subtotalCents: 11988,
      discountCents: 3596,
      amountCents: 8392,
      maskedInstrument: "Visa •••• 4242",
      invoiceNumber: "INV-2026-NO2FA",
      paymentStatus: "authorized",
      createdAt: created,
    })
  );

  // Ghost PCI fields (cvv / cardNumber) rejected
  await assertFails(
    aliceDb.collection("billingPayments").doc("pay_pci_leak").set({
      userId: ALICE_UID,
      providerId: ALICE_UID,
      planTier: "business_elite",
      billingCycle: "monthly",
      paymentMethod: "visa",
      currency: "USD",
      subtotalCents: 999,
      discountCents: 0,
      amountCents: 999,
      maskedInstrument: "Visa •••• 4242",
      invoiceNumber: "INV-2026-0001",
      paymentStatus: "authorized",
      verificationId: "ver_pay_1",
      cvv: "123",
      createdAt: created,
    })
  );

  // Unmasked 16-digit PAN inside maskedInstrument rejected
  await assertFails(
    aliceDb.collection("billingPayments").doc("pay_raw_pan").set({
      userId: ALICE_UID,
      providerId: ALICE_UID,
      planTier: "business_elite",
      billingCycle: "monthly",
      paymentMethod: "mastercard",
      currency: "USD",
      subtotalCents: 999,
      discountCents: 0,
      amountCents: 999,
      maskedInstrument: "5500000000000004",
      invoiceNumber: "INV-2026-0002",
      paymentStatus: "authorized",
      verificationId: "ver_pay_1",
      createdAt: created,
    })
  );

  // Fake discount math (claiming $80 discount on $9.99 monthly) rejected
  await assertFails(
    aliceDb.collection("billingPayments").doc("pay_bad_math").set({
      userId: ALICE_UID,
      providerId: ALICE_UID,
      planTier: "business_elite",
      billingCycle: "monthly",
      paymentMethod: "paypal",
      currency: "USD",
      subtotalCents: 999,
      discountCents: 800,
      amountCents: 199,
      maskedInstrument: "PayPal alice@example.com",
      invoiceNumber: "INV-2026-0003",
      paymentStatus: "authorized",
      verificationId: "ver_pay_1",
      createdAt: created,
    })
  );

  // Valid Business Elite Yearly Visa receipt (30% off $119.88 -> $83.92) succeeds with verificationId
  await assertSucceeds(
    aliceDb.collection("billingPayments").doc("pay_valid_1").set({
      userId: ALICE_UID,
      providerId: ALICE_UID,
      planTier: "business_elite",
      billingCycle: "yearly",
      paymentMethod: "visa",
      currency: "USD",
      subtotalCents: 11988,
      discountCents: 3596,
      amountCents: 8392,
      maskedInstrument: "Visa •••• 4242",
      invoiceNumber: "INV-2026-8392",
      paymentStatus: "authorized",
      verificationId: "ver_pay_1",
      createdAt: created,
    })
  );

  // Owner scoped query succeeds; unfiltered query, cross-user read, unauth read, update, and delete ALL fail
  await assertSucceeds(aliceDb.collection("billingPayments").where("userId", "==", ALICE_UID).get());
  await assertFails(aliceDb.collection("billingPayments").get());
  await assertFails(bobDb.collection("billingPayments").doc("pay_valid_1").get());
  await assertFails(unauthDb.collection("billingPayments").doc("pay_valid_1").get());
  await assertFails(
    aliceDb.collection("billingPayments").doc("pay_valid_1").update({
      amountCents: 0,
    })
  );
  await assertFails(aliceDb.collection("billingPayments").doc("pay_valid_1").delete());
});

test("FinancialTwoFactor: providerSecurity secret-leak denial, stale authTimestamp blocking, and immutable 2FA audit trail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const created = new Date(Date.now() - 8000);
  const validSha256 = "a".repeat(64);

  // Plaintext Base32 secret instead of 64-char SHA-256 hex digest is rejected
  await assertFails(
    aliceDb.collection("providerSecurity").doc(ALICE_UID).set({
      userId: ALICE_UID,
      providerId: ALICE_UID,
      twoFactorEnabled: true,
      twoFactorType: "totp_authenticator",
      secretFingerprint: "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Extra raw secret ghost field rejected
  await assertFails(
    aliceDb.collection("providerSecurity").doc(ALICE_UID).set({
      userId: ALICE_UID,
      providerId: ALICE_UID,
      twoFactorEnabled: true,
      twoFactorType: "totp_authenticator",
      secretFingerprint: validSha256,
      rawSecret: "JBSWY3DPEHPK3PXP",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid owner 2FA metadata succeeds; cross-user / unauth / delete rejected
  await assertSucceeds(
    aliceDb.collection("providerSecurity").doc(ALICE_UID).set({
      userId: ALICE_UID,
      providerId: ALICE_UID,
      twoFactorEnabled: true,
      twoFactorType: "totp_authenticator",
      secretFingerprint: validSha256,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(bobDb.collection("providerSecurity").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("providerSecurity").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("providerSecurity").doc(ALICE_UID).delete());

  // Stale Firebase Auth timestamp (>15 min old) rejected on financialVerifications
  const staleAuthTime = Math.floor(Date.now() / 1000) - 2000;
  await assertFails(
    aliceDb.collection("financialVerifications").doc("ver_stale").set({
      userId: ALICE_UID,
      providerId: ALICE_UID,
      verificationMethod: "firebase_reauth_and_totp",
      reauthenticated: true,
      totpVerified: true,
      firebaseAuthTimeSeconds: staleAuthTime,
      actionType: "activate_subscription",
      verifiedAt: created,
    })
  );

  // Unverified Factor-2 (totpVerified = false) rejected
  const freshAuthTime = Math.floor(Date.now() / 1000) - 5;
  await assertFails(
    aliceDb.collection("financialVerifications").doc("ver_unverified").set({
      userId: ALICE_UID,
      providerId: ALICE_UID,
      verificationMethod: "firebase_reauth_and_totp",
      reauthenticated: true,
      totpVerified: false,
      firebaseAuthTimeSeconds: freshAuthTime,
      actionType: "activate_subscription",
      verifiedAt: created,
    })
  );

  // Valid verification succeeds, and cannot be updated, deleted, or stolen by Bob
  await assertSucceeds(
    aliceDb.collection("financialVerifications").doc("ver_good").set({
      userId: ALICE_UID,
      providerId: ALICE_UID,
      verificationMethod: "firebase_reauth_and_totp",
      reauthenticated: true,
      totpVerified: true,
      firebaseAuthTimeSeconds: freshAuthTime,
      actionType: "switch_plan",
      verifiedAt: created,
    })
  );
  await assertFails(bobDb.collection("financialVerifications").doc("ver_good").get());
  await assertFails(
    aliceDb.collection("financialVerifications").doc("ver_good").update({ totpVerified: false })
  );
  await assertFails(aliceDb.collection("financialVerifications").doc("ver_good").delete());
});

test("PreBookingChat: real-time threads & messages enforce participant isolation, anti-spoofing, batch integrity, and query scoping", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext("eve_789").firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 5000);

  // Seed Bob's published provider listing
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("providerProfiles").doc(BOB_UID).set({
      userId: BOB_UID,
      businessName: "Bob Electrical & Solar",
      categorySlug: "solar-inverter",
      categoryName: "Solar & Inverter",
      countryCode: "ZW",
      city: "Harare",
      listingStatus: "published",
      verificationStatus: "not_submitted",
      createdAt: now,
      updatedAt: now,
    });
  });

  const threadId = `${BOB_UID}__${ALICE_UID}`;

  // Self-chat (Bob messaging his own provider listing) denied
  await assertFails(
    bobDb.collection("chatThreads").doc(`${BOB_UID}__${BOB_UID}`).set({
      customerId: BOB_UID,
      providerId: BOB_UID,
      providerUserId: BOB_UID,
      participantIds: [BOB_UID, BOB_UID],
      customerName: "Bob",
      providerBusinessName: "Bob Electrical & Solar",
      providerCategoryName: "Solar & Inverter",
      lastMessageText: "Self message",
      lastSenderId: BOB_UID,
      messageCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Atomic WriteBatch: Alice initiates pre-booking chat thread + first inquiry message
  const initBatch = aliceDb.batch();
  initBatch.set(aliceDb.collection("chatThreads").doc(threadId), {
    customerId: ALICE_UID,
    providerId: BOB_UID,
    providerUserId: BOB_UID,
    participantIds: [ALICE_UID, BOB_UID],
    customerName: "Alice Moyo",
    providerBusinessName: "Bob Electrical & Solar",
    providerCategoryName: "Solar & Inverter",
    lastMessageText: "Hi Bob, can you install a 5kVA inverter this Friday?",
    lastSenderId: ALICE_UID,
    messageCount: 1,
    lastCustomerReadAt: now,
    createdAt: now,
    updatedAt: now,
  });
  initBatch.set(aliceDb.collection("chatThreads").doc(threadId).collection("messages").doc("msg_1"), {
    threadId,
    providerId: BOB_UID,
    providerUserId: BOB_UID,
    customerId: ALICE_UID,
    participantIds: [ALICE_UID, BOB_UID],
    senderId: ALICE_UID,
    senderName: "Alice Moyo",
    body: "Hi Bob, can you install a 5kVA inverter this Friday?",
    messageType: "pre_booking_inquiry",
    clientSentMillis: Date.now() - 5000,
    sentAt: now,
  });
  await assertSucceeds(initBatch.commit());

  // Both Alice and Bob can query their scoped inbox & subcollection messages
  await assertSucceeds(
    aliceDb.collection("chatThreads").where("participantIds", "array-contains", ALICE_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("chatThreads").where("participantIds", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("chatThreads").doc(threadId).collection("messages")
      .where("participantIds", "array-contains", BOB_UID).get()
  );

  // Third party (Eve) & unauthenticated user CANNOT read, list, or inject into thread
  await assertFails(eveDb.collection("chatThreads").doc(threadId).get());
  await assertFails(
    eveDb.collection("chatThreads").where("participantIds", "array-contains", ALICE_UID).get()
  );
  await assertFails(
    eveDb.collection("chatThreads").doc(threadId).collection("messages")
      .where("participantIds", "array-contains", BOB_UID).get()
  );
  await assertFails(unauthDb.collection("chatThreads").doc(threadId).get());

  // Unfiltered list queries rejected
  await assertFails(aliceDb.collection("chatThreads").get());
  await assertFails(aliceDb.collection("chatThreads").doc(threadId).collection("messages").get());

  // Bob replies via atomic batch updating thread preview & messageCount
  const replyTime = new Date(Date.now() - 2000);
  const replyBatch = bobDb.batch();
  replyBatch.update(bobDb.collection("chatThreads").doc(threadId), {
    lastMessageText: "Yes Alice! Friday 10:00 AM works, and cables are included.",
    lastSenderId: BOB_UID,
    messageCount: 2,
    lastProviderReadAt: replyTime,
    updatedAt: replyTime,
  });
  replyBatch.set(bobDb.collection("chatThreads").doc(threadId).collection("messages").doc("msg_2"), {
    threadId,
    providerId: BOB_UID,
    providerUserId: BOB_UID,
    customerId: ALICE_UID,
    participantIds: [ALICE_UID, BOB_UID],
    senderId: BOB_UID,
    senderName: "Bob Electrical & Solar",
    body: "Yes Alice! Friday 10:00 AM works, and cables are included.",
    messageType: "text",
    clientSentMillis: Date.now() - 2000,
    sentAt: replyTime,
  });
  await assertSucceeds(replyBatch.commit());

  // Spoofing senderId (Bob pretending to be Alice) rejected
  await assertFails(
    bobDb.collection("chatThreads").doc(threadId).collection("messages").doc("msg_spoof").set({
      threadId,
      providerId: BOB_UID,
      providerUserId: BOB_UID,
      customerId: ALICE_UID,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alice Moyo",
      body: "Spoofed message",
      messageType: "text",
      clientSentMillis: Date.now() - 1000,
      sentAt: new Date(Date.now() - 1000),
    })
  );

  // Oversized message (> 1000 chars) rejected
  await assertFails(
    aliceDb.collection("chatThreads").doc(threadId).collection("messages").doc("msg_big").set({
      threadId,
      providerId: BOB_UID,
      providerUserId: BOB_UID,
      customerId: ALICE_UID,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alice Moyo",
      body: "x".repeat(1001),
      messageType: "text",
      clientSentMillis: Date.now() - 1000,
      sentAt: new Date(Date.now() - 1000),
    })
  );

  // Sent messages & threads are immutable against edit/delete
  await assertFails(
    aliceDb.collection("chatThreads").doc(threadId).collection("messages").doc("msg_1").update({ body: "Edited" })
  );
  await assertFails(
    aliceDb.collection("chatThreads").doc(threadId).collection("messages").doc("msg_1").delete()
  );
  await assertFails(aliceDb.collection("chatThreads").doc(threadId).delete());
});

test("CommunityChat: everyone signed in can communicate with strict 3-second server cooldown enforcement", async () => {
  const firebaseCompat = (() => {
    try {
      const app = require("firebase/compat/app");
      require("firebase/compat/firestore");
      return app;
    } catch (_) {
      const app = require("/usr/local/lib/node_modules/@firebase/rules-unit-testing/node_modules/firebase/compat/app");
      require("/usr/local/lib/node_modules/@firebase/rules-unit-testing/node_modules/firebase/compat/firestore");
      return app;
    }
  })();
  const serverTs = () => firebaseCompat.firestore.FieldValue.serverTimestamp();

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  // Unauthenticated access denied
  await assertFails(unauthDb.collection("communityMessages").get());
  await assertFails(unauthDb.collection("chatRateLimits").doc(ALICE_UID).get());

  // Posting without updating /chatRateLimits/{uid} in batch is denied
  await assertFails(
    aliceDb.collection("communityMessages").doc("no_gate_msg").set({
      senderId: ALICE_UID,
      senderName: "Alice Moyo",
      senderRole: "member",
      body: "Trying to bypass cooldown gate",
      clientSentMillis: Date.now(),
      sentAt: serverTs(),
    })
  );

  // First message from Alice via atomic batch succeeds
  const batch1 = aliceDb.batch();
  batch1.set(aliceDb.collection("chatRateLimits").doc(ALICE_UID), {
    userId: ALICE_UID,
    lastMessageAt: serverTs(),
  });
  batch1.set(aliceDb.collection("communityMessages").doc("cmsg_1"), {
    senderId: ALICE_UID,
    senderName: "Alice Moyo",
    senderRole: "member",
    body: "Hello everyone! Looking for reliable solar installers in Harare.",
    clientSentMillis: Date.now(),
    sentAt: serverTs(),
  });
  await assertSucceeds(batch1.commit());

  // Second message from Alice immediately (< 3 seconds later) MUST be rejected with PERMISSION_DENIED
  const burstBatch = aliceDb.batch();
  burstBatch.update(aliceDb.collection("chatRateLimits").doc(ALICE_UID), {
    lastMessageAt: serverTs(),
  });
  burstBatch.set(aliceDb.collection("communityMessages").doc("cmsg_burst"), {
    senderId: ALICE_UID,
    senderName: "Alice Moyo",
    senderRole: "member",
    body: "Spamming immediately within 3 seconds!",
    clientSentMillis: Date.now(),
    sentAt: serverTs(),
  });
  await assertFails(burstBatch.commit());

  // Bob (another user) is NOT blocked by Alice's cooldown and can read & reply immediately
  await assertSucceeds(bobDb.collection("communityMessages").get());
  const bobBatch = bobDb.batch();
  bobBatch.set(bobDb.collection("chatRateLimits").doc(BOB_UID), {
    userId: BOB_UID,
    lastMessageAt: serverTs(),
  });
  bobBatch.set(bobDb.collection("communityMessages").doc("cmsg_bob_1"), {
    senderId: BOB_UID,
    senderName: "Bob Electrical",
    senderRole: "provider",
    body: "Hi Alice! Feel free to message me or book directly.",
    clientSentMillis: Date.now(),
    sentAt: serverTs(),
  });
  await assertSucceeds(bobBatch.commit());

  // Spoofing senderId, reading another user's rate limit, editing, or deleting is denied
  await assertFails(bobDb.collection("chatRateLimits").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("communityMessages").doc("cmsg_1").update({ body: "Tampered" }));
  await assertFails(aliceDb.collection("communityMessages").doc("cmsg_1").delete());

  // Once Alice's /chatRateLimits/{uid}.lastMessageAt is >= 3 seconds old, her next message succeeds
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("chatRateLimits").doc(ALICE_UID).set({
      userId: ALICE_UID,
      lastMessageAt: new Date(Date.now() - 4000),
    });
  });
  const afterCooldownBatch = aliceDb.batch();
  afterCooldownBatch.update(aliceDb.collection("chatRateLimits").doc(ALICE_UID), {
    lastMessageAt: serverTs(),
  });
  afterCooldownBatch.set(aliceDb.collection("communityMessages").doc("cmsg_alice_2"), {
    senderId: ALICE_UID,
    senderName: "Alice Moyo",
    senderRole: "member",
    body: "Thanks Bob! Checking your profile now.",
    clientSentMillis: Date.now(),
    sentAt: serverTs(),
  });
  await assertSucceeds(afterCooldownBatch.commit());
});
