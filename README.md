## Movies ##

A simple backend code example for my job application.

### Introduction ###

The code is divided into two parts: [backend](backend/) which was the main focus of this exercise and [frontend](frontend/) which was a little extra. The code is almost 100% by Claude Code.

I have documented our conversation with Claude in `ai-prompts.md` during the development on both ends. Claude has also generated a `plan.md` based on my initial prompt and answers.

Claude also generated `ai-usage.md` to describe how AI was used, my contributions were mostly making notes on these files.

### For the reader ###

The vast amount of code that was produced is overwhelming, so here are some key files and sections to read:

 - [`backend/documentation/ai-usage.md`](backend/documentation/ai-usage.md) Section "Developer's notes": My own notes
 - [`backend/documentation/ai-prompts.md`](backend/documentation/ai-prompts.md): Prompts I used to get the code. It does not contain the discussion I later had about some points (below)
 - [`backend/README.md`](backend/README.md): If you want to try out the server yourself this file contains detailed instructions
 - [`backend/documentation/plan.md`](backend/documentation/plan.md): Plan Claude produced from my initial prompt and which was updated during the development. Includes some notes about future extension.
 - [`backend/src/main/resources/openapi/openapi.yaml`](backend/src/main/resources/openapi/openapi.yaml): The backend follows contract-first principle. This file is the source of truth and describes the API.
 - [`backend/src/main/java/.../api/MoviesController.java`](backend/src/main/java/com/example/movies/api/MoviesController.java): Implementation of the aforementioned API
 - [`backend/src/main/java/.../seed/MovieImporter.java`](backend/src/main/java/com/example/movies/seed/MovieImporter.java): Importer for the given `movies-compact.json`
 - Frontend: [`frontend/README.md`](frontend/README.md) (how to run it), [`frontend/documentation/ai-usage.md`](frontend/documentation/ai-usage.md) (section "Developer's notes"), [`frontend/documentation/ai-prompts.md`](frontend/documentation/ai-prompts.md) and [`frontend/documentation/plan.md`](frontend/documentation/plan.md)

### Points for discussion ###

 - Choices of technology: Spring Boot, PostgreSQL, OpenAPI, (TypeScript, React)
 - `Genre` has an entity of its own, but `MovieDto.genres` is only a list of strings. Name of the genre is its key. This works in this scale and listing and adding genres on movies will be easy to implement,
   but if we ever wanted to implement any kind of management (remove, rename) for genres, we should move into using proper ids. Change like this in the early unpublished stage of the API and contract would
   be cheap, but challenging in production. This is something that should be taken into consideration in planning.
 - As soon as I started developing the frontend, I noticed some problems in the contract and had to return back to changing it. This was due to my own laziness and would not be ok if the contract was shared with
   e.g. other teams or clients.
 - `MovieRepository.search` requires using `toContainsPattern` to properly work. Even when this is described in the JavaDoc, I would prefer not to have to trust the next developer (most likely me) to read it but
   enforce its usage some way. Solution to this was discussed but not tested with Claude.
 - `@Query` in the repository works for 20 movies, but will soon get really slow. Better alternatives were discussed and documented by Claude in the plan. I also checked that the Query will not provide exploit for
   SQL injection.
 - `MovieImporter` fixes a typo in a genre name that Claude found while analyzing the data. Such analysis on bigger data might not be possible so we would have to make a decision: accept typos or try to fix only
   known ones. Either way this would lead to the need for genre management (rename, merge) and thus to the first point above.
 - `movies-compact.json` is, as name suggests, compact and contains only 20 entries. I have not tested the import with bigger files. JSON might not be the best possible format to bring in bigger files.
 - Initially Claude ran some tests against the local backend which led to problems. I requested "virtual database" and learnt that was actually the Testcontainer Claude was proposing earlier :)
 - Claude produced a huge amount of tests and, honestly, I have had no time to read through all of them with some thought. They seem to make sense and test all the layers on their own and the whole application.
   I have not run any coverage tool on the tests, but I was surprised Claude even ran some mutation tests (manual mutation by deliberately breaking the code) to see if the tests failed so I'm starting to trust it more than myself.
 - Frontend was developed quickly and I was again surprised how easy it was. I requested only two additions and we already have a fully functional demo.
 - Time spent on this was roughly: 5 hours for backend, 3 hours for frontend and 5 hours for reviewing the code and discussing the solution with Claude.
