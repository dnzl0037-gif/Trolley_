package com.example.data.catalog

data class ServiceCategory(
    val id: String,
    val slug: String,
    val name: String,
    val family: String,
    val description: String,
)

object ServiceTaxonomy {

    val categoryFamilies: List<Pair<String, List<String>>> = listOf(
        "Plumbing" to listOf(
            "Leak detection", "Pipe installation", "Drain cleaning", "Toilet repair",
            "Tap and faucet repair", "Water heater repair", "Burst pipe repair", "Bathroom plumbing",
            "Kitchen plumbing", "Water pressure repair", "Sewer line service", "Blocked sink clearing",
            "Shower installation", "Water tank installation", "Pump installation", "Gas line plumbing",
            "Backflow testing", "Pipe relining", "Emergency plumbing", "Plumbing inspection"
        ),
        "Electrical" to listOf(
            "Wiring installation", "Electrical fault finding", "Lighting installation", "Socket installation",
            "Circuit breaker repair", "Distribution board upgrade", "Generator connection", "Electrical inspection",
            "Ceiling fan installation", "Outdoor lighting", "Surge protection", "Backup power systems",
            "Appliance circuit installation", "Electrical rewiring", "Safety switch testing", "Security lighting",
            "Commercial electrical", "Emergency electrician", "Solar electrical connection", "Smart home wiring"
        ),
        "Cleaning" to listOf(
            "Home deep cleaning", "Office cleaning", "Move-in cleaning", "Move-out cleaning",
            "Carpet cleaning", "Upholstery cleaning", "Window cleaning", "After-build cleaning",
            "Kitchen cleaning", "Bathroom sanitising", "Floor polishing", "Pressure washing",
            "Mattress cleaning", "Airbnb turnover cleaning", "Retail cleaning", "Industrial cleaning",
            "Water damage cleanup", "Disinfection service", "Regular housekeeping", "Post-event cleaning"
        ),
        "Carpentry" to listOf(
            "Custom cabinetry", "Door installation", "Door repair", "Window frames",
            "Furniture making", "Furniture repair", "Kitchen cabinets", "Wardrobe installation",
            "Deck construction", "Wooden flooring", "Staircase construction", "Shelving installation",
            "Wood restoration", "Pergola construction", "Roof trusses", "Wooden gates",
            "Skirting installation", "Timber framing", "Joinery finishing", "Custom woodwork"
        ),
        "Painting" to listOf(
            "Interior painting", "Exterior painting", "Roof painting", "Commercial painting",
            "Residential repainting", "Decorative finishes", "Spray painting", "Waterproof coatings",
            "Wall preparation", "Crack filling", "Ceiling painting", "Fence painting",
            "Floor coating", "Texture painting", "Wood staining", "Metal painting",
            "Paint stripping", "Wallpaper removal", "Wallpaper installation", "Colour consultation"
        ),
        "Gardening" to listOf(
            "Lawn mowing", "Garden design", "Tree pruning", "Tree removal",
            "Hedge trimming", "Irrigation installation", "Lawn installation", "Garden maintenance",
            "Landscaping", "Weed control", "Compost setup", "Planting service",
            "Garden cleanup", "Paving and edging", "Water feature installation", "Turf care",
            "Vegetable garden setup", "Stump grinding", "Soil improvement", "Outdoor planting"
        ),
        "Construction" to listOf(
            "New home construction", "Building extensions", "Renovations", "Foundation work",
            "Bricklaying", "Concrete work", "Structural repairs", "Project management",
            "Site preparation", "Boundary walls", "General contracting", "Building inspections",
            "Suspended ceilings", "Partition installation", "Commercial construction", "Residential construction",
            "Retaining walls", "Masonry restoration", "Small building works", "Construction supervision"
        ),
        "Roofing" to listOf(
            "Roof leak repair", "Roof replacement", "Tile roofing", "Metal roofing",
            "Roof inspections", "Gutter installation", "Gutter cleaning", "Roof waterproofing",
            "Flat roof systems", "Roof ventilation", "Fascia and soffit repair", "Roof painting",
            "Skylight installation", "Storm damage repair", "Roof insulation", "Roof flashing",
            "Roof truss repair", "Downpipe installation", "Roof maintenance", "Solar roof preparation"
        ),
        "Tiling" to listOf(
            "Bathroom tiling", "Kitchen tiling", "Floor tiling", "Wall tiling",
            "Outdoor tiling", "Tile replacement", "Grout repair", "Tile sealing",
            "Mosaic installation", "Natural stone tiling", "Pool tiling", "Splashback installation",
            "Large-format tiles", "Tile levelling", "Waterproofing under tiles", "Tile polishing",
            "Tile removal", "Commercial tiling", "Shower tiling", "Tile skirting"
        ),
        "Flooring" to listOf(
            "Laminate flooring", "Vinyl flooring", "Hardwood flooring", "Floor sanding",
            "Floor polishing", "Epoxy flooring", "Carpet installation", "Carpet repair",
            "Floor levelling", "Tile floor installation", "Bamboo flooring", "Engineered wood flooring",
            "Floor refinishing", "Concrete polishing", "Floor waterproofing", "Commercial flooring",
            "Floor removal", "Skirting boards", "Floor inspection", "Sports flooring"
        ),
        "Welding" to listOf(
            "Gate welding", "Trailer welding", "Structural welding", "Aluminium welding",
            "Stainless steel welding", "Steel repairs", "Mobile welding", "Security gate fabrication",
            "Bakkie canopy repairs", "Pipe welding", "Metal furniture welding", "Stair rail welding",
            "Industrial welding", "Arc welding", "MIG welding", "TIG welding",
            "Welding inspections", "Equipment repairs", "On-site fabrication", "Weld finishing"
        ),
        "Metal fabrication" to listOf(
            "Custom gates", "Steel window frames", "Metal doors", "Burglar bars",
            "Metal staircases", "Balustrades", "Steel carports", "Metal shelving",
            "Trailer fabrication", "Custom brackets", "Sheet metal work", "Handrails",
            "Metal signage frames", "Security screens", "Machine guards", "Steel structures",
            "Aluminium fabrication", "Metal cutting", "Metal bending", "Custom metalwork"
        ),
        "Automotive repair" to listOf(
            "Engine diagnostics", "Brake repairs", "Clutch repairs", "Suspension repairs",
            "Auto electrical repairs", "Gearbox repairs", "Cooling system repairs", "Fuel system repairs",
            "Exhaust repairs", "Steering repairs", "Vehicle diagnostics", "Starter motor repair",
            "Alternator repair", "Radiator repair", "Pre-purchase inspection", "Mobile mechanic",
            "Engine rebuilds", "Transmission service", "Air conditioning repair", "Roadside mechanical help"
        ),
        "Vehicle maintenance" to listOf(
            "Oil change", "Vehicle servicing", "Tyre rotation", "Wheel alignment",
            "Wheel balancing", "Battery testing", "Brake inspection", "Vehicle inspection",
            "Fleet maintenance", "Air filter replacement", "Wiper replacement", "Coolant flush",
            "Transmission servicing", "Vehicle detailing", "Headlight restoration", "Tyre fitting",
            "Puncture repair", "Roadworthy preparation", "Motorcycle servicing", "Preventive maintenance"
        ),
        "Moving" to listOf(
            "House removals", "Office relocations", "Furniture moving", "Long-distance moving",
            "Packing services", "Unpacking services", "Piano moving", "Appliance moving",
            "Storage transport", "Student moves", "Small-load moving", "Loading assistance",
            "Furniture assembly", "Moving supplies", "Commercial relocation", "Fragile-item packing",
            "Local removals", "International moving coordination", "Debris removal", "Move planning"
        ),
        "Delivery" to listOf(
            "Same-day delivery", "Courier services", "Document delivery", "Parcel delivery",
            "Furniture delivery", "Appliance delivery", "Grocery delivery", "Business deliveries",
            "Scheduled delivery", "Medical supply delivery", "Bulk item delivery", "Intercity delivery",
            "Last-mile delivery", "Collection services", "Express courier", "E-commerce fulfilment",
            "Motorbike courier", "Dedicated vehicle delivery", "Fragile parcel delivery", "Delivery route support"
        ),
        "Pest control" to listOf(
            "Termite treatment", "Cockroach control", "Ant control", "Rodent control",
            "Bed bug treatment", "Spider control", "Mosquito control", "Wasp nest removal",
            "Flea treatment", "Bird deterrents", "Commercial pest control", "Residential pest control",
            "Pest inspections", "Fumigation", "Pre-construction treatment", "Stored product pests",
            "Snake removal", "Integrated pest management", "Pest prevention", "Follow-up treatment"
        ),
        "Security" to listOf(
            "Security guard services", "Alarm installation", "CCTV installation", "Access control",
            "Electric fencing", "Gate automation", "Security risk assessment", "Alarm monitoring",
            "Intercom installation", "Security patrols", "Biometric access", "Smart locks",
            "Perimeter security", "Fire alarm installation", "Security system repair", "Event security",
            "Residential security", "Commercial security", "Camera maintenance", "Security consulting"
        ),
        "Computer repair" to listOf(
            "Laptop repair", "Desktop repair", "Screen replacement", "Keyboard replacement",
            "Data recovery", "Virus removal", "Operating system installation", "Computer upgrades",
            "Hardware diagnostics", "Printer repair", "Computer cleaning", "Power supply repair",
            "Motherboard diagnostics", "SSD installation", "Laptop battery replacement", "Wi-Fi troubleshooting",
            "Computer setup", "Business computer support", "Backup setup", "Device maintenance"
        ),
        "IT support" to listOf(
            "Network setup", "Wi-Fi installation", "Server administration", "Cloud migration",
            "IT helpdesk", "Cybersecurity assessment", "Email configuration", "Microsoft 365 support",
            "Data backup", "Network troubleshooting", "Firewall configuration", "IT consulting",
            "Remote IT support", "Business continuity", "Device management", "IT procurement advice",
            "Domain setup", "VoIP setup", "IT maintenance", "Disaster recovery planning"
        ),
        "Web development" to listOf(
            "Business websites", "E-commerce websites", "Website redesign", "Landing pages",
            "Website maintenance", "WordPress development", "Web application development", "Booking websites",
            "Website speed optimisation", "Website accessibility", "Website hosting setup", "API integration",
            "Payment integration", "CMS development", "Website migration", "Frontend development",
            "Backend development", "Website security audit", "SEO technical fixes", "Website support"
        ),
        "Graphic design" to listOf(
            "Logo design", "Brand identity", "Business stationery", "Social media graphics",
            "Flyer design", "Brochure design", "Packaging design", "Poster design",
            "Presentation design", "Infographics", "Menu design", "Signage design",
            "Illustration", "Business cards", "Vehicle branding", "Email templates",
            "Label design", "Ad creative design", "Print design", "Brand guidelines"
        ),
        "Photography" to listOf(
            "Wedding photography", "Event photography", "Portrait photography", "Product photography",
            "Corporate photography", "Real estate photography", "Family photography", "Graduation photography",
            "Food photography", "Commercial photography", "Headshots", "Maternity photography",
            "Newborn photography", "School photography", "Sports photography", "Architectural photography",
            "Photo editing", "Passport photos", "Property listings photography", "Photography packages"
        ),
        "Videography" to listOf(
            "Wedding videography", "Event videography", "Corporate videos", "Product videos",
            "Promotional videos", "Interview filming", "Real estate videos", "Music videos",
            "Social media reels", "Training videos", "Drone videography", "Live streaming",
            "Video editing", "Documentary filming", "Animation production", "Testimonial videos",
            "Conference recording", "Short-form content", "Commercial filming", "Video post-production"
        ),
        "Marketing" to listOf(
            "Digital marketing strategy", "Social media management", "Search engine optimisation", "Google Ads management",
            "Facebook Ads management", "Content marketing", "Email marketing", "Lead generation",
            "Marketing analytics", "Brand strategy", "Influencer campaigns", "Local marketing",
            "Market research", "Conversion optimisation", "Campaign management", "Copywriting",
            "Marketing automation", "Public relations", "Customer acquisition", "Marketing consulting"
        ),
        "Accounting" to listOf(
            "Annual financial statements", "Tax return preparation", "Management accounts", "Payroll accounting",
            "Financial reporting", "Company registration support", "Tax planning", "VAT services",
            "Audit preparation", "Cash flow planning", "Budget preparation", "Accounting software setup",
            "Business accounting", "Individual accounting", "Tax compliance", "Financial forecasting",
            "Accounting reviews", "Bookkeeping oversight", "Financial advice", "Accounting consultation"
        ),
        "Bookkeeping" to listOf(
            "Monthly bookkeeping", "Invoice processing", "Bank reconciliations", "Supplier reconciliations",
            "Customer accounts", "Expense tracking", "Payroll processing", "Cashbook maintenance",
            "Management reports", "Debtors management", "Creditors management", "Receipt capture",
            "Accounting data cleanup", "Inventory bookkeeping", "VAT reconciliations", "Cloud bookkeeping setup",
            "Bookkeeping catch-up", "Small business bookkeeping", "Financial record organisation", "Bookkeeping consultation"
        ),
        "Legal services" to listOf(
            "Contract drafting", "Contract review", "Business legal advice", "Labour law advice",
            "Family law consultation", "Property law consultation", "Debt recovery", "Company secretarial services",
            "Legal document preparation", "Dispute resolution", "Mediation", "Estate planning",
            "Conveyancing support", "Intellectual property advice", "Compliance advice", "Civil litigation support",
            "Legal research", "Notarial services", "Immigration legal advice", "Legal consultation"
        ),
        "Business consulting" to listOf(
            "Business plans", "Business process improvement", "Strategy consulting", "Operations consulting",
            "Market entry research", "Business registration guidance", "Financial planning", "Franchise consulting",
            "Risk management", "Human resources consulting", "Procurement consulting", "Sales strategy",
            "Growth planning", "Feasibility studies", "Performance management", "Change management",
            "Business coaching", "Management consulting", "Startup advisory", "Business diagnostics"
        ),
        "Education" to listOf(
            "Early childhood tutoring", "Primary school support", "Secondary school support", "Adult education",
            "Study skills coaching", "Exam preparation", "Language lessons", "Computer literacy",
            "Special needs learning support", "Homeschool support", "Reading support", "Writing support",
            "STEM enrichment", "Career guidance", "Online lessons", "Learning assessments",
            "Homework support", "Educational workshops", "Study planning", "Academic mentoring"
        ),
        "Tutoring" to listOf(
            "Mathematics tutoring", "Physics tutoring", "Chemistry tutoring", "Biology tutoring",
            "English tutoring", "Shona tutoring", "Ndebele tutoring", "Accounting tutoring",
            "Economics tutoring", "Geography tutoring", "History tutoring", "Computer science tutoring",
            "Primary maths tutoring", "Primary English tutoring", "A-level tutoring", "O-level tutoring",
            "University tutoring", "Exam revision", "Online tutoring", "Private home tutoring"
        ),
        "Childcare" to listOf(
            "Babysitting", "Full-time nanny", "Part-time nanny", "After-school care",
            "Daycare services", "Holiday childcare", "Newborn care", "Special needs childcare",
            "School collection", "Childminding", "In-home childcare", "Overnight childcare",
            "Child activity supervision", "Meal preparation for children", "Childcare consultation", "Emergency childcare",
            "Weekend childcare", "Playgroup supervision", "Homework supervision", "Childcare placement"
        ),
        "Elder care" to listOf(
            "Companion care", "In-home personal care", "Mobility assistance", "Meal preparation",
            "Medication reminders", "Respite care", "Elder transport", "Daily living assistance",
            "Overnight care", "Dementia support care", "Post-hospital support", "Shopping assistance",
            "Light housekeeping", "Care needs assessment", "Wellness check-ins", "Appointment accompaniment",
            "Live-in care", "Daytime supervision", "Family caregiver relief", "Elder care coordination"
        ),
        "Beauty" to listOf(
            "Facial treatments", "Manicure", "Pedicure", "Waxing",
            "Skin consultations", "Body treatments", "Eyebrow shaping", "Eyelash extensions",
            "Nail extensions", "Nail art", "Beauty therapy", "Makeup services",
            "Mobile beauty services", "Bridal beauty", "Acne treatments", "Skin rejuvenation",
            "Beauty consultations", "Spa treatments", "Brow tinting", "Lash lifting"
        ),
        "Hairdressing" to listOf(
            "Haircuts", "Hair colouring", "Highlights", "Balayage",
            "Hair relaxers", "Hair treatments", "Braiding", "Dreadlock maintenance",
            "Wig installation", "Hair extensions", "Blow-dry styling", "Hair straightening",
            "Natural hair care", "Barber services", "Children haircuts", "Bridal hairstyling",
            "Protective styling", "Hair consultation", "Scalp treatments", "Mobile hairdressing"
        ),
        "Makeup" to listOf(
            "Bridal makeup", "Event makeup", "Natural makeup", "Editorial makeup",
            "Photoshoot makeup", "Prom makeup", "Special effects makeup", "Mature skin makeup",
            "Makeup lessons", "Mobile makeup", "Traditional wedding makeup", "Graduation makeup",
            "Fashion makeup", "TV makeup", "Glam makeup", "Makeup trials",
            "Group makeup", "Corporate makeup", "Makeup consultation", "Makeup touch-ups"
        ),
        "Fitness" to listOf(
            "Personal training", "Group fitness classes", "Strength training", "Weight training coaching",
            "Cardio training", "Mobility training", "Fitness assessments", "Home workout coaching",
            "Online fitness coaching", "Bootcamp classes", "Functional fitness", "Sports conditioning",
            "Beginner fitness", "Senior fitness", "Posture training", "Fitness programme design",
            "Gym instruction", "Fitness goal coaching", "Small group training", "Fitness consultation"
        ),
        "Personal training" to listOf(
            "One-to-one coaching", "Weight loss coaching", "Muscle building plans", "Strength programmes",
            "Running coaching", "Athletic conditioning", "Bodyweight training", "Home personal training",
            "Online personal training", "Fitness testing", "Exercise technique coaching", "Beginner programmes",
            "Senior personal training", "Core strength coaching", "Flexibility coaching", "Sports performance coaching",
            "Workout planning", "Accountability coaching", "Personal fitness assessment", "Training programme review"
        ),
        "Catering" to listOf(
            "Wedding catering", "Corporate catering", "Private party catering", "Buffet catering",
            "Cocktail catering", "Traditional cuisine catering", "Lunch catering", "Breakfast catering",
            "Outdoor event catering", "Funeral catering", "School catering", "Meal preparation",
            "Private chef services", "Mobile food catering", "Vegetarian catering", "Vegan catering",
            "BBQ catering", "Catering equipment hire", "Menu planning", "Catering consultation"
        ),
        "Baking" to listOf(
            "Birthday cakes", "Wedding cakes", "Cupcakes", "Custom cakes",
            "Bread baking", "Pastry baking", "Dessert tables", "Cookies and biscuits",
            "Traditional baked goods", "Gluten-free baking", "Vegan baking", "Cake decorating",
            "Cake pops", "Celebration desserts", "Corporate baking orders", "Pies and tarts",
            "Doughnut orders", "Baking classes", "Dessert catering", "Custom bakery orders"
        ),
        "Event planning" to listOf(
            "Wedding planning", "Corporate event planning", "Birthday party planning", "Conference planning",
            "Exhibition planning", "Private celebrations", "Event coordination", "Venue sourcing",
            "Event budgeting", "Guest management", "Event styling", "Supplier coordination",
            "Launch events", "Graduation parties", "Community events", "Fundraising events",
            "Event logistics", "Event consultation", "On-the-day coordination", "Event production"
        ),
        "Interior design" to listOf(
            "Residential interior design", "Commercial interior design", "Space planning", "Kitchen design",
            "Bathroom design", "Office design", "Furniture selection", "Colour consultation",
            "Lighting design", "3D interior visualisation", "Decor sourcing", "Room makeovers",
            "Retail interior design", "Hospitality design", "Interior styling", "Custom storage design",
            "Renovation planning", "Interior consultation", "Window treatment design", "Interior project coordination"
        ),
        "Architecture" to listOf(
            "Residential architecture", "Commercial architecture", "Building plans", "Architectural drawings",
            "Concept design", "Renovation design", "Site analysis", "Planning applications support",
            "3D architectural visualisation", "Sustainable design", "Interior architecture", "Space planning",
            "Construction documentation", "Building alterations", "Feasibility design", "Architectural consultation",
            "Measured building surveys", "Project coordination", "Design development", "As-built drawings"
        ),
        "Engineering" to listOf(
            "Civil engineering consulting", "Structural engineering", "Electrical engineering", "Mechanical engineering",
            "Quantity surveying", "Geotechnical consulting", "Water engineering", "Project engineering",
            "Engineering inspections", "Design calculations", "Site supervision", "Engineering reports",
            "Industrial engineering", "Environmental engineering", "Road design", "Drainage design",
            "HVAC engineering", "Engineering drawings", "Technical due diligence", "Engineering consultation"
        ),
        "Air conditioning" to listOf(
            "Air conditioner installation", "Air conditioner repair", "Air conditioner servicing", "Refrigerant leak repair",
            "Duct cleaning", "Duct installation", "Commercial HVAC maintenance", "Residential HVAC maintenance",
            "Heat pump installation", "Ventilation installation", "Air conditioning diagnostics", "Aircon gas refill",
            "Thermostat repair", "AC electrical fault repair", "Split unit installation", "Cassette unit servicing",
            "HVAC inspections", "Airflow balancing", "Aircon removal", "Preventive HVAC maintenance"
        ),
        "Appliance repair" to listOf(
            "Fridge repair", "Freezer repair", "Washing machine repair", "Dishwasher repair",
            "Oven repair", "Stove repair", "Microwave repair", "Tumble dryer repair",
            "Cooker hood repair", "Water dispenser repair", "Small appliance repair", "Appliance installation",
            "Appliance diagnostics", "Refrigerator gas service", "Electric kettle repair", "Vacuum cleaner repair",
            "Appliance maintenance", "Commercial appliance repair", "Appliance safety checks", "Appliance collection service"
        ),
        "Laundry" to listOf(
            "Wash and fold", "Dry cleaning", "Ironing services", "Bedding laundry",
            "Curtain cleaning", "Laundry collection", "Laundry delivery", "Commercial laundry",
            "Hotel linen service", "Workwear cleaning", "Delicate garment care", "Stain removal",
            "Shoe cleaning", "Duvet cleaning", "Laundry subscription service", "Self-service laundry support",
            "Laundry pressing", "Uniform cleaning", "Laundry alterations coordination", "Laundry consultation"
        ),
        "Tailoring" to listOf(
            "Clothing alterations", "Suit alterations", "Dress alterations", "Wedding dress alterations",
            "School uniform alterations", "Zip replacement", "Clothing repairs", "Custom clothing",
            "Traditional wear tailoring", "Curtain alterations", "Hem adjustments", "Leather alterations",
            "Made-to-measure suits", "Pattern making", "Garment resizing", "Bridal tailoring",
            "Uniform tailoring", "Clothing restyling", "Express alterations", "Tailoring consultation"
        ),
        "Furniture repair" to listOf(
            "Sofa repair", "Chair repair", "Table restoration", "Cabinet repair",
            "Furniture polishing", "Wood furniture restoration", "Upholstery repair", "Leather furniture repair",
            "Drawer repair", "Furniture assembly", "Antique furniture restoration", "Bed frame repair",
            "Office furniture repair", "Furniture refinishing", "Furniture reupholstery", "Furniture touch-ups",
            "Furniture inspection", "Outdoor furniture repair", "Furniture modification", "Custom furniture repair"
        ),
        "Solar installation" to listOf(
            "Solar system design", "Residential solar installation", "Commercial solar installation", "Solar panel cleaning",
            "Inverter installation", "Battery installation", "Solar maintenance", "Solar fault diagnosis",
            "Off-grid solar systems", "Hybrid solar systems", "Solar water heating", "Solar system upgrades",
            "Solar inspections", "Solar monitoring setup", "Battery backup design", "Solar panel replacement",
            "Solar wiring inspection", "Energy usage assessment", "Solar consultation", "Solar system commissioning"
        ),
        "General maintenance" to listOf(
            "Handyman services", "Property maintenance", "Door and lock repairs", "Minor plumbing repairs",
            "Minor electrical repairs", "Wall mounting", "Flat-pack assembly", "Minor plastering",
            "Caulking and sealing", "Fence repairs", "Paving repairs", "Gutter maintenance",
            "Property inspections", "Rental property maintenance", "Office maintenance", "Preventive maintenance",
            "Minor roof repairs", "Fixture installation", "Home repair visits", "Maintenance planning"
        ),
    )

    val familyNames: List<String> = categoryFamilies.map { it.first }

    val categories: List<ServiceCategory> = categoryFamilies.flatMapIndexed { familyIndex, (family, subs) ->
        subs.mapIndexed { subIndex, name ->
            ServiceCategory(
                id = "cat-${familyIndex + 1}-${subIndex + 1}",
                slug = toSlug(family, name),
                name = name,
                family = family,
                description = "$name from qualified local service providers"
            )
        }
    }

    val subcategoryCount: Int = categories.size

    fun toSlug(family: String, name: String): String {
        return "$family-$name"
            .lowercase()
            .replace("&", "and")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
    }

    fun findBySlug(slug: String): ServiceCategory? = categories.firstOrNull { it.slug == slug }

    fun filterServices(query: String, activeFamily: String): List<ServiceCategory> {
        val trimmed = query.trim().lowercase()
        val byFamily = if (activeFamily == "All services" || activeFamily.isBlank()) {
            categories
        } else {
            val directFamily = categories.filter { it.family.equals(activeFamily, ignoreCase = true) }
            if (directFamily.isNotEmpty()) directFamily else categories
        }
        if (trimmed.isBlank()) {
            return if (activeFamily == "All services" || activeFamily.isBlank()) emptyList() else byFamily
        }
        return byFamily.filter {
            it.name.lowercase().contains(trimmed) ||
                it.family.lowercase().contains(trimmed) ||
                it.description.lowercase().contains(trimmed) ||
                it.slug.contains(trimmed)
        }
    }
}
