# Coding conventions

* Functions will often return their `output_dir` parameter. This allows for concise pipelining e.g.

```python
temp = f(input_dir=temp, output_dir=work_dir / "1")
temp = f(input_dir=temp, output_dir=work_dir / "2")
temp = f(input_dir=temp, output_dir=work_dir / "3")
```

* Imports:
  * We use the `black` Python code formatter and `isort` for sorting imports.
  * We also use the following import style, which is not automatically checked:

```python
import random  # Standard modules come first (isort takes care of this).
import re      # Import the module only, not classes or functions.
import subprocess
from pathlib import Path  # Exception: Path.
from typing import Iterable, List, Optional # Exception: typing.

from gfauto import (  # Use "from gfauto import MODULE".
    amber_converter,
    android_device,
    binaries_util,
    fuzz,
    gflogging,
    glsl_generate_util,
    host_device_util,
    interrupt_util,
    result_util,
    shader_compiler_util,
    shader_job_util,
    signature_util,
    spirv_opt_util,
    subprocess_util,
    test_util,
    tool,
    util,
)
from gfauto.device_pb2 import Device  # Exception: protobuf classes.
from gfauto.gflogging import log  # Exception: log.
from gfauto.settings_pb2 import Settings
from gfauto.test_pb2 import Test, TestGlsl
from gfauto.util import check  # Exception: check.
```

