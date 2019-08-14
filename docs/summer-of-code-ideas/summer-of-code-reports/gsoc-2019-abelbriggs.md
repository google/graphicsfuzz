# Google Summer of Code 2019 Report - Abel Briggs

###### Personal Links:
###### [Github](https://github.com/abelbriggs1) - [Linkedin](https://www.linkedin.com/in/abelbriggs/)

###### Proposal:
###### [Enhancing Metamorphic Testing Tools For Graphics Drivers](https://summerofcode.withgoogle.com/projects/#6749896743321600)

###### Mentors: 
###### Alasdair Donaldson, Hugues Evrard

## Preface

Before this report begins, I'd like to deliver a short spiel directed towards potential future
viewers (and likely GSoC hopefuls) of this document.

While Google Summer of Code may look daunting to the prospective student applicant, I implore anyone
who even has a passing interest in the program to consider applying/proposing for an organization
that strikes your fancy (and do not be intimidated when looking at past GSoC projects!). Mentorship 
is an invaluable experience to any engineer (especially junior engineers) and not all workplaces 
have the budget and/or manpower to support it. The best feature of Google Summer of Code is that 
this problem vanishes - you are paired up with mentors that have 'help my student succeed' as a 
major goal, and this is a fundamental cornerstone of the program. I had little experience in 
proper software collaboration and in working on large/domain-specific codebases before I began 
submitting PRs to GraphicsFuzz - the code reviews and mentoring that I was given during the program 
were instrumental in making me a better and more confident engineer.

With that said...

## Deliverables

During and throughout the summer of 2019, my mentors and I worked out the kinks in my original
proposal and developed some fairly solid objectives to work towards. This report is intended to
detail what those deliverables are, how they were delivered and what could still use work in the
future.

These deliverables were:

 - Enhance GraphicsFuzz's shader generator by making it more aware of OpenGL Shader Language's
 built-in functions.
 - Enhance GraphicsFuzz's shader generator by adding additional ways to generate opaque values and
 new identity transformations.
 - Add a new 'worker' program that takes GraphicsFuzz shader jobs from a server, renders the shader
 job via [Mesa](https://mesa.freedesktop.org/)'s open-source test framework
 [Piglit](https://piglit.freedesktop.org/), and sends the results back to the server.
 - Add new shaders to GraphicsFuzz's fuzz test set - new shaders as well as derivatives of the
  original test set.
 - Apply GraphicsFuzz to the Mesa open-source graphics driver suite, specifically the nVIDIA
 reverse-engineered driver [nouveau](https://nouveau.freedesktop.org/wiki/).
 
### Enhance GraphicsFuzz's shader generator GLSL built-in support

When GraphicsFuzz generates fuzzed/garbage expressions with the assumption that they won't be
executed (e.g. in (true ? x : y), y will never be executed), it is able to generate calls to GLSL
built-in functions(think abs(), sqrt(), etc.) with garbage values as arguments. To do this, however,
GraphicsFuzz needs to know what types to fuzz expressions for, or it will cause syntax errors.
The following PRs involve cross-checking built-in functions with the language version they were
introduced in, then adding their function prototypes to GraphicsFuzz.

[#521](https://github.com/google/graphicsfuzz/pull/521):
Add built-in support for GLSL Integer Functions

[#528](https://github.com/google/graphicsfuzz/pull/528):
Add built-in function support for GLSL Vector Relational Functions

[#549](https://github.com/google/graphicsfuzz/pull/549):
Add built-in function support for GLSL 3.20 Fragment Processing Functions

[#563](https://github.com/google/graphicsfuzz/pull/563):
Refactor exponential builtins into separate function

Additionally, minor issues arised from adding support for these functions - some of these functions
involve using 'out' parameters and modify said parameters during their execution. This would
potentially cause 'side effects' - lvalues being modified when not expected. The following PR
relates to mitigating this issue.

[#533](https://github.com/google/graphicsfuzz/pull/533):
Check for side effects with lvalue parameters of built-in functions

#### Leftovers

The following issues are nits or minor problems related to built-ins that were left unfixed due to 
time or knowledge constraints.

[#550](https://github.com/google/graphicsfuzz/issues/550):
Ensure certain function prototypes can use non-uniform shader input variables

[#570](https://github.com/google/graphicsfuzz/issues/570):
Be less conservative about when FunctionCallExprTemplates yield expressions that have side effects.









