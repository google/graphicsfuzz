# @generated by generate_proto_mypy_stubs.py.  Do not edit!
import sys
from gfauto.common_pb2 import (
    ArchiveSet as gfauto___common_pb2___ArchiveSet,
)

from google.protobuf.descriptor import (
    Descriptor as google___protobuf___descriptor___Descriptor,
)

from google.protobuf.internal.containers import (
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


class ArtifactMetadata(google___protobuf___message___Message):
    DESCRIPTOR: google___protobuf___descriptor___Descriptor = ...
    class Data(google___protobuf___message___Message):
        DESCRIPTOR: google___protobuf___descriptor___Descriptor = ...

        @property
        def extracted_archive_set(self) -> ArtifactMetadataExtractedArchiveSet: ...

        def __init__(self,
            *,
            extracted_archive_set : typing___Optional[ArtifactMetadataExtractedArchiveSet] = None,
            ) -> None: ...
        @classmethod
        def FromString(cls, s: builtin___bytes) -> ArtifactMetadata.Data: ...
        def MergeFrom(self, other_msg: google___protobuf___message___Message) -> None: ...
        def CopyFrom(self, other_msg: google___protobuf___message___Message) -> None: ...
        if sys.version_info >= (3,):
            def HasField(self, field_name: typing_extensions___Literal[u"data",u"extracted_archive_set"]) -> builtin___bool: ...
            def ClearField(self, field_name: typing_extensions___Literal[u"data",u"extracted_archive_set"]) -> None: ...
        else:
            def HasField(self, field_name: typing_extensions___Literal[u"data",b"data",u"extracted_archive_set",b"extracted_archive_set"]) -> builtin___bool: ...
            def ClearField(self, field_name: typing_extensions___Literal[u"data",b"data",u"extracted_archive_set",b"extracted_archive_set"]) -> None: ...
        def WhichOneof(self, oneof_group: typing_extensions___Literal[u"data",b"data"]) -> typing_extensions___Literal["extracted_archive_set"]: ...

    derived_from = ... # type: google___protobuf___internal___containers___RepeatedScalarFieldContainer[typing___Text]
    comment = ... # type: typing___Text

    @property
    def data(self) -> ArtifactMetadata.Data: ...

    def __init__(self,
        *,
        data : typing___Optional[ArtifactMetadata.Data] = None,
        derived_from : typing___Optional[typing___Iterable[typing___Text]] = None,
        comment : typing___Optional[typing___Text] = None,
        ) -> None: ...
    @classmethod
    def FromString(cls, s: builtin___bytes) -> ArtifactMetadata: ...
    def MergeFrom(self, other_msg: google___protobuf___message___Message) -> None: ...
    def CopyFrom(self, other_msg: google___protobuf___message___Message) -> None: ...
    if sys.version_info >= (3,):
        def HasField(self, field_name: typing_extensions___Literal[u"data"]) -> builtin___bool: ...
        def ClearField(self, field_name: typing_extensions___Literal[u"comment",u"data",u"derived_from"]) -> None: ...
    else:
        def HasField(self, field_name: typing_extensions___Literal[u"data",b"data"]) -> builtin___bool: ...
        def ClearField(self, field_name: typing_extensions___Literal[u"comment",b"comment",u"data",b"data",u"derived_from",b"derived_from"]) -> None: ...

class ArtifactMetadataExtractedArchiveSet(google___protobuf___message___Message):
    DESCRIPTOR: google___protobuf___descriptor___Descriptor = ...

    @property
    def archive_set(self) -> gfauto___common_pb2___ArchiveSet: ...

    def __init__(self,
        *,
        archive_set : typing___Optional[gfauto___common_pb2___ArchiveSet] = None,
        ) -> None: ...
    @classmethod
    def FromString(cls, s: builtin___bytes) -> ArtifactMetadataExtractedArchiveSet: ...
    def MergeFrom(self, other_msg: google___protobuf___message___Message) -> None: ...
    def CopyFrom(self, other_msg: google___protobuf___message___Message) -> None: ...
    if sys.version_info >= (3,):
        def HasField(self, field_name: typing_extensions___Literal[u"archive_set"]) -> builtin___bool: ...
        def ClearField(self, field_name: typing_extensions___Literal[u"archive_set"]) -> None: ...
    else:
        def HasField(self, field_name: typing_extensions___Literal[u"archive_set",b"archive_set"]) -> builtin___bool: ...
        def ClearField(self, field_name: typing_extensions___Literal[u"archive_set",b"archive_set"]) -> None: ...
