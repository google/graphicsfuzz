# -*- coding: utf-8 -*-
# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: gfauto/settings.proto

import sys
_b=sys.version_info[0]<3 and (lambda x:x) or (lambda x:x.encode('latin1'))
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from google.protobuf import reflection as _reflection
from google.protobuf import symbol_database as _symbol_database
# @@protoc_insertion_point(imports)

_sym_db = _symbol_database.Default()


from gfauto import common_pb2 as gfauto_dot_common__pb2
from gfauto import device_pb2 as gfauto_dot_device__pb2


DESCRIPTOR = _descriptor.FileDescriptor(
  name='gfauto/settings.proto',
  package='gfauto',
  syntax='proto3',
  serialized_options=None,
  serialized_pb=_b('\n\x15gfauto/settings.proto\x12\x06gfauto\x1a\x13gfauto/common.proto\x1a\x13gfauto/device.proto\"\x97\x03\n\x08Settings\x12\'\n\x0b\x64\x65vice_list\x18\x01 \x01(\x0b\x32\x12.gfauto.DeviceList\x12\'\n\x0f\x63ustom_binaries\x18\x02 \x03(\x0b\x32\x0e.gfauto.Binary\x12!\n\x19maximum_duplicate_crashes\x18\x03 \x01(\r\x12\x1d\n\x15maximum_fuzz_failures\x18\x04 \x01(\r\x12\x1b\n\x13reduce_tool_crashes\x18\x05 \x01(\x08\x12\x16\n\x0ereduce_crashes\x18\x06 \x01(\x08\x12\x19\n\x11reduce_bad_images\x18\x07 \x01(\x08\x12.\n\x16latest_binary_versions\x18\x08 \x03(\x0b\x32\x0e.gfauto.Binary\x12)\n!extra_graphics_fuzz_generate_args\x18\t \x03(\t\x12\'\n\x1f\x65xtra_graphics_fuzz_reduce_args\x18\n \x03(\t\x12#\n\x1bonly_reduce_signature_regex\x18\x0b \x01(\tb\x06proto3')
  ,
  dependencies=[gfauto_dot_common__pb2.DESCRIPTOR,gfauto_dot_device__pb2.DESCRIPTOR,])




_SETTINGS = _descriptor.Descriptor(
  name='Settings',
  full_name='gfauto.Settings',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='device_list', full_name='gfauto.Settings.device_list', index=0,
      number=1, type=11, cpp_type=10, label=1,
      has_default_value=False, default_value=None,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='custom_binaries', full_name='gfauto.Settings.custom_binaries', index=1,
      number=2, type=11, cpp_type=10, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='maximum_duplicate_crashes', full_name='gfauto.Settings.maximum_duplicate_crashes', index=2,
      number=3, type=13, cpp_type=3, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='maximum_fuzz_failures', full_name='gfauto.Settings.maximum_fuzz_failures', index=3,
      number=4, type=13, cpp_type=3, label=1,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='reduce_tool_crashes', full_name='gfauto.Settings.reduce_tool_crashes', index=4,
      number=5, type=8, cpp_type=7, label=1,
      has_default_value=False, default_value=False,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='reduce_crashes', full_name='gfauto.Settings.reduce_crashes', index=5,
      number=6, type=8, cpp_type=7, label=1,
      has_default_value=False, default_value=False,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='reduce_bad_images', full_name='gfauto.Settings.reduce_bad_images', index=6,
      number=7, type=8, cpp_type=7, label=1,
      has_default_value=False, default_value=False,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='latest_binary_versions', full_name='gfauto.Settings.latest_binary_versions', index=7,
      number=8, type=11, cpp_type=10, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='extra_graphics_fuzz_generate_args', full_name='gfauto.Settings.extra_graphics_fuzz_generate_args', index=8,
      number=9, type=9, cpp_type=9, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='extra_graphics_fuzz_reduce_args', full_name='gfauto.Settings.extra_graphics_fuzz_reduce_args', index=9,
      number=10, type=9, cpp_type=9, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='only_reduce_signature_regex', full_name='gfauto.Settings.only_reduce_signature_regex', index=10,
      number=11, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  serialized_options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=76,
  serialized_end=483,
)

_SETTINGS.fields_by_name['device_list'].message_type = gfauto_dot_device__pb2._DEVICELIST
_SETTINGS.fields_by_name['custom_binaries'].message_type = gfauto_dot_common__pb2._BINARY
_SETTINGS.fields_by_name['latest_binary_versions'].message_type = gfauto_dot_common__pb2._BINARY
DESCRIPTOR.message_types_by_name['Settings'] = _SETTINGS
_sym_db.RegisterFileDescriptor(DESCRIPTOR)

Settings = _reflection.GeneratedProtocolMessageType('Settings', (_message.Message,), {
  'DESCRIPTOR' : _SETTINGS,
  '__module__' : 'gfauto.settings_pb2'
  # @@protoc_insertion_point(class_scope:gfauto.Settings)
  })
_sym_db.RegisterMessage(Settings)


# @@protoc_insertion_point(module_scope)
