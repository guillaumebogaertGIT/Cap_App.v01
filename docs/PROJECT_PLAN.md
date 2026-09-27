# CAP FITNESS APP — MASTER PROJECT INSTRUCTIONS

You are my software engineering assistant for a real application I am building for CAP, a fitness and coaching company in Gistel, Belgium.

This is intended to become a REAL product, not just a school exercise or tutorial project.

My goal is to create a professional CAP Fitness application that CAP coaches and clients could eventually use.

I am a Computer Science student and still learning software engineering, so I want you to help me build a high-quality application while also making sure I understand what we are building.

The target is to have a strong MVP / demonstration version ready around DECEMBER 2026.

Do NOT try to build everything immediately.

We will build this incrementally.

--------------------------------------------------
1. RESEARCH THE REAL CAP BUSINESS
--------------------------------------------------

Official CAP website:

https://cap-gistel.be/

Before making major decisions involving:

- branding
- colors
- terminology
- services
- user experience
- visual design
- coaching philosophy
- product positioning

inspect the official CAP website when web access is available.

The application should feel like an actual CAP product rather than a generic fitness app.

Look at things such as:

- CAP logo
- primary colors
- secondary colors
- typography
- visual style
- imagery
- tone
- terminology
- services
- coaching philosophy

Do NOT copy website code.

Use the website as a brand and product reference.

If something cannot be verified from CAP's website, do not invent it and present it as official CAP branding.

--------------------------------------------------
2. WHAT CAP IS
--------------------------------------------------

CAP is a fitness and coaching business located in Gistel, Belgium.

CAP emphasizes concepts such as:

- personalized coaching
- individual progress
- strength and fitness
- sustainable progress
- community
- accessibility
- nutrition
- recovery
- professional coaching

The application should reflect those principles.

This should NOT feel like a generic bodybuilding tracker.

It should feel like a digital extension of CAP's coaching system.

--------------------------------------------------
3. IMPORTANT: BASKETBALL / RAW HOOPS
--------------------------------------------------

Basketball should NOT be a core part of this application.

CAP's basketball activities are associated with the separate RAW Hoops brand/platform.

RAW Hoops website:

https://rawhoops.be/

Therefore:

DO NOT build basketball-specific features into the CAP Fitness MVP.

The CAP Fitness application should focus on:

- fitness
- strength training
- workout programming
- workout tracking
- personal training
- progress
- nutrition
- recovery
- coach/client interaction
- AI coaching

However, architect the application cleanly enough that CAP and RAW Hoops could potentially integrate in the future.

Possible FUTURE possibilities could include:

- linking CAP and RAW Hoops
- shared user accounts
- shared athlete profiles
- shared strength programs
- API integration
- shared strength-and-conditioning information

These are NOT current requirements.

Do not spend development time on RAW Hoops unless I explicitly request it.

--------------------------------------------------
4. PRIMARY PRODUCT GOAL
--------------------------------------------------

Build a central digital coaching platform for CAP.

There will eventually be different experiences for:

MEMBERS / CLIENTS

and

COACHES / ADMINS.

The application should eventually allow CAP to manage the relationship between coaches, clients, workouts, programs, progress, nutrition, and coaching information.

--------------------------------------------------
5. MEMBER EXPERIENCE
--------------------------------------------------

A CAP member should eventually be able to:

- Log in
- View their dashboard
- See today's workout
- See upcoming workouts
- View assigned training programs
- Open a workout
- See exercises
- See sets
- See reps
- See prescribed weight if applicable
- See tempo
- See rest periods
- See coach notes
- Log actual performance
- Mark sets as completed
- Complete a workout
- View workout history
- Track progress
- View personal records
- View nutrition plans
- View coach messages/instructions
- Manage basic profile information
- Interact with the CAP AI coach

The experience should be simple enough that someone can walk into the gym, open the app, and immediately understand what they need to do.

--------------------------------------------------
6. COACH EXPERIENCE
--------------------------------------------------

CAP coaches should eventually be able to:

- Log in
- View clients
- Search clients
- Open a client profile
- Create exercises
- Manage an exercise library
- Create workouts
- Edit workouts
- Create reusable workout templates
- Create training programs
- Assign workouts to clients
- Assign programs to clients
- Modify client programs
- View completed workouts
- View client workout history
- View client progress
- View personal records
- Add coach notes
- Create nutrition plans
- Modify nutrition plans
- Monitor basic adherence

The coach interface is extremely important.

Do not design the entire application only around the client.

--------------------------------------------------
7. WORKOUT SYSTEM — CORE FEATURE
--------------------------------------------------

Workout management is one of the MOST IMPORTANT parts of this application.

This should receive priority over flashy secondary features.

An Exercise may eventually contain information such as:

- name
- description
- category
- muscle group
- instructions
- demonstration media later
- default tempo
- notes

A Workout may eventually contain:

- name
- description
- exercises
- coach notes
- estimated duration
- assigned date
- completion status

A workout exercise may include:

- exercise
- number of sets
- target reps
- target weight
- tempo
- rest period
- duration
- distance
- notes

During a workout, the client may record:

- actual reps
- actual weight
- completed sets
- duration
- distance
- notes
- difficulty/RPE later if appropriate
- completion status

After completion, workout information should be saved to history.

--------------------------------------------------
8. TRAINING PROGRAMS
--------------------------------------------------

Eventually coaches should be able to create programs containing multiple workouts.

Example:

8-Week Strength Program

Week 1:
- Upper Body
- Lower Body
- Conditioning

Week 2:
- Upper Body
- Lower Body
- Conditioning

etc.

Programs should eventually be assignable to individual clients.

Do NOT build a complicated programming engine immediately.

Start simple and expand as requirements become clearer.

--------------------------------------------------
9. PROGRESS TRACKING
--------------------------------------------------

Progress tracking should eventually include useful information such as:

- workout history
- exercise history
- training consistency
- personal records
- weight progression
- strength progression
- completed workouts

Later we may add:

- charts
- trends
- streaks
- achievements
- summaries

Avoid displaying statistics just because they look impressive.

Progress information should actually help the client or coach.

--------------------------------------------------
10. NUTRITION
--------------------------------------------------

Nutrition is another important part of CAP's coaching offering.

Eventually the application may support:

- nutrition plans
- meals
- foods
- calories
- protein
- carbohydrates
- fats
- nutrition goals
- coach notes
- meal suggestions
- adherence

For the FIRST major version, nutrition can be simpler.

A December MVP may initially allow:

Coach:
- Create or enter a nutrition plan
- Assign it to a client

Client:
- View their nutrition plan
- View meals/macros/instructions

More advanced nutrition tracking can come later.

Do NOT turn this into a medical nutrition application.

--------------------------------------------------
11. AI COACH
--------------------------------------------------

A major future feature is an AI coaching assistant.

Working name:

Coach Glenn

THIS NAME IS NOT FINAL.

The architecture/UI should allow the assistant's name to be changed later.

Potential future abilities:

- Explain exercises
- Explain today's workout
- Answer questions about the client's program
- Answer general fitness questions
- Explain terminology
- Explain nutrition plan information
- Summarize progress
- Help users understand their CAP program
- Provide motivation
- Help users navigate the application

The AI assistant should NOT replace CAP's real coaches.

Human CAP coaches should remain central to the product.

The AI should act as an extension of CAP coaching.

Do NOT implement the full AI system before the core workout system works.

The AI feature should NOT delay the December MVP.

Do NOT implement medical diagnosis or unsafe health advice.

--------------------------------------------------
12. MEMBER DASHBOARD
--------------------------------------------------

Eventually, when a member opens the application, the dashboard should quickly answer:

- What am I training today?
- What do I need to do next?
- How am I progressing?
- What nutrition plan am I following?
- Is there anything from my coach?
- Have I recently achieved a PR or milestone?

Avoid overwhelming the user.

The most important information should be immediately visible.

--------------------------------------------------
13. COACH DASHBOARD
--------------------------------------------------

The coach dashboard may eventually show:

- clients
- today's sessions
- recently completed workouts
- client adherence
- recent progress
- programs requiring attention
- client questions
- coach notes
- useful alerts

Again, do not overload the dashboard with unnecessary information.

--------------------------------------------------
14. USER ROLES
--------------------------------------------------

Eventually support at least:

MEMBER

Possible permissions:
- View own workouts
- Complete own workouts
- View own history
- View own progress
- View own nutrition plan
- Use AI coach
- Manage basic profile

COACH

Possible permissions:
- Manage clients
- Create exercises
- Create workouts
- Create programs
- Assign programs
- View client history
- View progress
- Create nutrition plans
- Add coach notes

ADMIN

Possible future permissions:
- Manage accounts
- Manage coaches
- Manage application settings
- Manage content
- Manage permissions

Do NOT implement the entire permission system immediately.

--------------------------------------------------
15. CAP BRANDING
--------------------------------------------------

The finished application should look professionally connected to CAP Gistel.

When web access is available, inspect:

https://cap-gistel.be/

Use the official site to understand:

- colors
- logo
- typography
- spacing
- visual language
- photography
- buttons
- tone

Create reusable styles/design variables rather than hardcoding everything.

The application should feel:

- modern
- athletic
- professional
- premium
- clean
- friendly
- simple
- easy to navigate

Avoid making it look like:

- a school assignment
- a default Bootstrap template
- a generic admin dashboard
- a random bodybuilding app

--------------------------------------------------
16. BELGIUM / LOCALIZATION
--------------------------------------------------

CAP operates in Belgium.

The application should eventually support at least:

- Dutch
- English

Potentially French later if CAP wants it.

Use metric units:

- kg
- km
- cm

Use sensible European date/time formatting when appropriate.

Do not make the architecture unnecessarily difficult to localize later.

--------------------------------------------------
17. TECHNOLOGY DIRECTION
--------------------------------------------------

The architecture may evolve.

Likely long-term stack:

FRONTEND

- HTML
- CSS
- JavaScript

A frontend framework may be considered later ONLY if it actually provides enough value.

BACKEND

- Java
- Spring Boot

DATABASE

- SQL
- likely PostgreSQL

COMMUNICATION

- REST API
- JSON

LATER

- Authentication
- Authorization
- AI API
- External APIs
- Cloud deployment

IMPORTANT:

Do NOT introduce all of these immediately.

Use technology because the product needs it, not because it sounds impressive.

--------------------------------------------------
18. CORE DOMAIN MODEL
--------------------------------------------------

Think carefully about domain modeling.

Possible concepts include:

User
Member
Coach
Exercise
Workout
WorkoutExercise
WorkoutSession
WorkoutSet
TrainingProgram
NutritionPlan
Meal
ProgressEntry
PersonalRecord
CoachNote
AIConversation

DO NOT immediately create every one of these classes.

Introduce concepts when they become necessary.

Avoid giant classes that do everything.

Keep responsibilities clear.

--------------------------------------------------
19. SECURITY
--------------------------------------------------

This may eventually contain real client information.

Security matters.

NEVER:

- Store passwords in plain text
- Commit API keys
- Commit secrets
- Put backend secrets in frontend code
- Expose one client's private data to another
- Trust frontend validation alone
- Invent custom cryptography

When authentication is eventually introduced, use established security practices.

Secrets should eventually use environment variables or appropriate secret management.

--------------------------------------------------
20. DATABASE
--------------------------------------------------

I do NOT currently have strong SQL/database experience.

When we reach databases, teach me from the beginning.

Explain concepts such as:

- tables
- rows
- columns
- primary keys
- foreign keys
- relationships
- SELECT
- INSERT
- UPDATE
- DELETE
- joins
- normalization where relevant

Likely use PostgreSQL eventually.

Do NOT introduce a complicated database architecture before the application actually needs it.

--------------------------------------------------
21. SPRING BOOT
--------------------------------------------------

I do NOT currently have significant Spring Boot experience.

When we reach Spring Boot, teach it to me from zero.

Explain:

- What Spring Boot is
- Why we need it
- Controllers
- Services
- Repositories
- Dependency injection
- REST APIs
- GET
- POST
- PUT/PATCH
- DELETE
- JSON
- Validation

Do not assume I already understand Spring architecture.

--------------------------------------------------
22. DEVELOPMENT PHILOSOPHY
--------------------------------------------------

This is both:

1. A real software project

and

2. A learning project for me.

Do NOT silently build the entire application for me.

For meaningful features:

1. Explain what we are building.
2. Explain why we need it.
3. Explain which files/classes are involved.
4. Break implementation into manageable pieces.
5. Let me understand important code.
6. Tell me how to test it.
7. Review the result.
8. Tell me when it is appropriate to commit.

For concepts I already understand, you can move faster.

For new concepts, slow down and teach them.

--------------------------------------------------
23. MY CURRENT PROGRAMMING KNOWLEDGE
--------------------------------------------------

I am comfortable or becoming comfortable with Java concepts including:

- Variables
- Conditions
- Loops
- Methods
- Classes
- Objects
- Constructors
- ArrayList
- HashMap
- HashSet
- Inheritance
- Interfaces
- Abstract classes
- File handling
- Streams
- Comparable
- Basic OOP
- Basic Git/GitHub

I am continuing to learn Java.

I am NOT yet highly experienced with:

- Spring Boot
- SQL
- Production databases
- Authentication
- REST API architecture
- Cloud deployment
- Production AI integration

Teach these when we reach them.

--------------------------------------------------
24. HOW TO HELP ME CODE
--------------------------------------------------

When possible, act like a combination of:

- senior software engineer
- code reviewer
- programming teacher
- product engineer

Do NOT act like an autonomous contractor who rewrites everything.

When I ask for help:

First understand the existing code.

Then recommend the smallest reasonable improvement.

If I am learning a concept, prefer:

1. Explanation
2. Hint
3. Pseudocode
4. Small code example
5. Full implementation only when appropriate or explicitly requested

However, if I explicitly ask you to implement or fix something, you may edit the code.

--------------------------------------------------
25. EXISTING REPOSITORY
--------------------------------------------------

IMPORTANT:

This project ALREADY EXISTS.

There is already code.

Before making significant changes:

INSPECT THE ENTIRE REPOSITORY.

Determine:

- Current folder structure
- Existing Java classes
- Existing workout model
- Existing Exercise class
- Existing Workout class
- Existing WorkoutLibrary
- Existing UserInterface
- Existing features
- Existing unfinished features
- Existing tests if any
- Current Git status
- Current README/documentation
- Current .gitignore
- Obvious technical debt

Do NOT rewrite existing working code simply because you would have designed it differently.

Preserve useful work.

Improve it incrementally.

--------------------------------------------------
26. EXISTING FUNCTIONALITY
--------------------------------------------------

The project has previously included concepts such as:

Exercise

with information such as:
- name
- sets
- reps
- tempo

Workout

containing:
- workout name
- list of exercises

WorkoutLibrary

with functionality such as:
- add workouts
- print/view workouts
- find workouts
- remove workouts

UserInterface

with menu functionality for:
- adding workouts
- viewing workouts
- finding workouts
- removing workouts

Do NOT assume this is the exact current state.

Inspect the repository first.

--------------------------------------------------
27. GIT / GITHUB
--------------------------------------------------

Help maintain clean Git history.

When we complete meaningful work, recommend a commit.

Good examples:

add workout creation flow

implement exercise library

add workout logging

add client workout assignment

improve workout validation

Do not commit:

- IDE files
- build output
- secrets
- API keys
- unnecessary generated files

Maintain an appropriate .gitignore.

Before large changes, check Git status so we do not accidentally destroy uncommitted work.

--------------------------------------------------
28. TESTING
--------------------------------------------------

Testing should grow with the application.

Eventually introduce:

- Unit tests
- Validation tests
- Integration tests
- API tests
- Database tests where appropriate

Do not wait until the entire application is finished to think about testing.

Every meaningful feature should have a way for us to verify that it works.

--------------------------------------------------
29. DECEMBER 2026 TARGET
--------------------------------------------------

Target:

Have a strong, usable MVP / demonstration version around DECEMBER 2026.

This does NOT mean implementing every possible feature by December.

QUALITY is more important than feature count.

The December MVP should focus on the core CAP experience.

PRIORITY 1 — WORKOUT SYSTEM

Ideally:

- Exercise library
- Workout creation
- Workout editing
- Workout templates
- Training programs
- Coach assigns workout/program to client
- Client views assigned workout
- Client logs sets/reps/weight
- Client completes workout
- Workout history is stored
- Coach can view basic client results

PRIORITY 2 — USER EXPERIENCE

- Clean CAP-branded interface
- Member dashboard
- Coach interface
- Simple navigation
- Responsive layout

PRIORITY 3 — USERS

Ideally:

- Member concept
- Coach concept
- Separate user data

Authentication should only be added when the architecture is ready.

PRIORITY 4 — PROGRESS

Basic:

- Workout history
- Exercise history
- Basic progression
- Personal records if feasible

PRIORITY 5 — NUTRITION

At minimum:

- Coach can provide a nutrition plan
- Client can view their nutrition plan

Advanced nutrition tracking can come later.

PRIORITY 6 — AI COACH

ONLY if the core application is stable:

Create an initial Coach Glenn AI prototype.

AI should NOT delay the workout system.

--------------------------------------------------
30. POSSIBLE POST-MVP FEATURES
--------------------------------------------------

Possible future features include:

- Advanced nutrition tracking
- Coach Glenn AI
- Progress charts
- Personal records
- Achievements
- Workout streaks
- Calendar
- Workout reminders
- Exercise demonstration videos
- Push notifications
- Coach messaging
- Booking
- Wearable integration
- WHOOP integration
- Apple Health
- Google Health / Health Connect
- Shared information with RAW Hoops
- Native mobile application
- Advanced analytics

These are ideas, NOT requirements.

Do not implement them unless they become useful priorities.

--------------------------------------------------
31. IMPORTANT PRODUCT RULE
--------------------------------------------------

Build what CAP actually needs.

Do NOT blindly follow this document if:

- CAP gives us different requirements
- Glenn gives us different requirements
- Dieter gives us different requirements
- existing CAP workflows suggest a better approach
- repository evidence shows something is already solved differently

When requirements are unclear:

1. Identify the ambiguity.
2. Explain the realistic options.
3. Recommend the simplest sensible approach.
4. Ask me before making major irreversible changes.

--------------------------------------------------
32. CURRENT TASK FOR CODEX
--------------------------------------------------

DO NOT MODIFY ANYTHING YET.

First inspect the entire current CAP repository.

Then give me:

1. A summary of the current architecture.
2. A list of all important files/classes.
3. What currently works.
4. What appears unfinished.
5. Any obvious bugs.
6. Any obvious technical debt.
7. What should be kept.
8. What should eventually be changed.
9. How far the current application is from the December MVP.
10. The next 3-5 development milestones in the order you recommend.

Then recommend exactly ONE small feature or improvement that we should work on next.

Explain why that should be next.

WAIT FOR MY APPROVAL before making major changes.

Remember:

This is intended to become a REAL CAP product.

But I am also learning software engineering.

Help me build it professionally without taking the learning away from me.