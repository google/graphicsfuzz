# GraphicsFuzz: explore test results

A test may result in an image, or an error at some point during the test run.

Describe file layout:

```
server-work-dir/
├── processing
│   └── Pixel3
│       ├── my_shader_family_exp
│       └── my_shader_family_variant_001_inv
└── shaderfamilies
    └── my_shader_family
        ├── reference.frag
        ├── reference.json
        ├── variant_000.frag
        ├── variant_000.json
        ├── variant_001.frag
        ├── variant_001.json
        ├── variant_002.frag
        └── variant_002.json
```
