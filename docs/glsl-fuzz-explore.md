# GraphicsFuzz: explore test results

When tests are executed via the web UI, the results are written to a particular
file hierarchy under the `processing` directory in the server working directory:

```shell
processing
└── <worker-name>
    ├── shader-family-01
    │   ├── reference.info.json
    │   ├── reference.png
    │   ├── reference.txt
    │   ├── variant_000.info.json
    │   ├── variant_000.png
    │   ├── variant_000.txt
    │   ├── variant_001.info.json
    │   ├── variant_001.png
    │   └── variant_001.txt
    └── shader-family-02
        ├── reference.info.js
        ├── reference.png
        ├── reference.txt
        ├── variant_000.info.json
        ├── variant_000.png
        ├── variant_000.txt
        ├── variant_001.info.json
        ├── variant_001.png
        └── variant_001.txt
```
