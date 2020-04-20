# @generated by generate_proto_mypy_stubs.py.  Do not edit!
import sys
from gfauto.common_pb2 import (
    Binary as gfauto___common_pb2___Binary,
)

from gfauto.device_pb2 import (
    Device as gfauto___device_pb2___Device,
)

from google.protobuf.descriptor import (
    Descriptor as google___protobuf___descriptor___Descriptor,
)

from google.protobuf.internal.containers import (
    RepeatedCompositeFieldContainer as google___protobuf___internal___containers___RepeatedCompositeFieldContainer,
    RepeatedScalarFieldContainer as google___protobuf___internal___containers___RepeatedScalarFieldContainer,
)

from google.protobuf.message import (
    Message as google___protobuf___message___Message,
)

from typing import (
    Iterable as typing___Iterable,
    Optional as typing___Optional,
    Text as typing___Text,
)

from typing_extensions import (
    Literal as typing_extensions___Literal,
)


builtin___bool = bool
builtin___bytes = bytes
builtin___float = float
builtin___int = int


class Test(google___protobuf___message___Message):
    DESCRIPTOR: google___protobuf___descriptor___Descriptor = ...
    crash_signature = ... # type: typing___Text
    expected_status = ... # type: typing___Text
    crash_regex_override = ... # type: typing___Text
    skip_validation = ... # type: builtin___bool
    derived_from = ... # type: typing___Text
    common_spirv_args = ... # type: google___protobuf___internal___containers___RepeatedScalarFieldContainer[typing___Text]

    @property
    def glsl(self) -> TestGlsl: ...

    @property
    def spirv_fuzz(self) -> TestSpirvFuzz: ...

    @property
    def device(self) -> gfauto___device_pb2___Device: ...

    @property
    def binaries(self) -> google___protobuf___internal___containers___RepeatedCompositeFieldContainer[gfauto___common_pb2___Binary]: ...

    def __init__(self,
        *,
        glsl : typing___Optional[TestGlsl] = None,
        spirv_fuzz : typing___Optional[TestSpirvFuzz] = None,
        crash_signature : typing___Optional[typing___Text] = None,
        device : typing___Optional[gfauto___device_pb2___Device] = None,
        binaries : typing___Optional[typing___Iterable[gfauto___common_pb2___Binary]] = None,
        expected_status : typing___Optional[typing___Text] = None,
        crash_regex_override : typing___Optional[typing___Text] = None,
        skip_validation : typing___Optional[builtin___bool] = None,
        derived_from : typing___Optional[typing___Text] = None,
        common_spirv_args : typing___Optional[typing___Iterable[typing___Text]] = None,
        ) -> None: ...
    @classmethod
    def FromString(cls, s: builtin___bytes) -> Test: ...
    def MergeFrom(self, other_msg: google___protobuf___message___Message) -> None: ...
    def CopyFrom(self, other_msg: google___protobuf___message___Message) -> None: ...
    if sys.version_info >= (3,):
        def HasField(self, field_name: typing_extensions___Literal[u"device",u"glsl",u"spirv_fuzz",u"test"]) -> builtin___bool: ...
        def ClearField(self, field_name: typing_extensions___Literal[u"binaries",u"common_spirv_args",u"crash_regex_override",u"crash_signature",u"derived_from",u"device",u"expected_status",u"glsl",u"skip_validation",u"spirv_fuzz",u"test"]) -> None: ...
    else:
        def HasField(self, field_name: typing_extensions___Literal[u"device",b"device",u"glsl",b"glsl",u"spirv_fuzz",b"spirv_fuzz",u"test",b"test"]) -> builtin___bool: ...
        def ClearField(self, field_name: typing_extensions___Literal[u"binaries",b"binaries",u"common_spirv_args",b"common_spirv_args",u"crash_regex_override",b"crash_regex_override",u"crash_signature",b"crash_signature",u"derived_from",b"derived_from",u"device",b"device",u"expected_status",b"expected_status",u"glsl",b"glsl",u"skip_validation",b"skip_validation",u"spirv_fuzz",b"spirv_fuzz",u"test",b"test"]) -> None: ...
    def WhichOneof(self, oneof_group: typing_extensions___Literal[u"test",b"test"]) -> typing_extensions___Literal["glsl","spirv_fuzz"]: ...

class TestGlsl(google___protobuf___message___Message):
    DESCRIPTOR: google___protobuf___descriptor___Descriptor = ...
    spirv_opt_args = ... # type: google___protobuf___internal___containers___RepeatedScalarFieldContainer[typing___Text]

    def __init__(self,
        *,
        spirv_opt_args : typing___Optional[typing___Iterable[typing___Text]] = None,
        ) -> None: ...
    @classmethod
    def FromString(cls, s: builtin___bytes) -> TestGlsl: ...
    def MergeFrom(self, other_msg: google___protobuf___message___Message) -> None: ...
    def CopyFrom(self, other_msg: google___protobuf___message___Message) -> None: ...
    if sys.version_info >= (3,):
        def ClearField(self, field_name: typing_extensions___Literal[u"spirv_opt_args"]) -> None: ...
    else:
        def ClearField(self, field_name: typing_extensions___Literal[u"spirv_opt_args",b"spirv_opt_args"]) -> None: ...

class TestSpirvFuzz(google___protobuf___message___Message):
    DESCRIPTOR: google___protobuf___descriptor___Descriptor = ...
    spirv_opt_args = ... # type: google___protobuf___internal___containers___RepeatedScalarFieldContainer[typing___Text]

    def __init__(self,
        *,
        spirv_opt_args : typing___Optional[typing___Iterable[typing___Text]] = None,
        ) -> None: ...
    @classmethod
    def FromString(cls, s: builtin___bytes) -> TestSpirvFuzz: ...
    def MergeFrom(self, other_msg: google___protobuf___message___Message) -> None: ...
    def CopyFrom(self, other_msg: google___protobuf___message___Message) -> None: ...
    if sys.version_info >= (3,):
        def ClearField(self, field_name: typing_extensions___Literal[u"spirv_opt_args"]) -> None: ...
    else:
        def ClearField(self, field_name: typing_extensions___Literal[u"spirv_opt_args",b"spirv_opt_args"]) -> None: ...
