// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: admingrpc.proto

package com.alipay.antchain.bridge.relayer.core.grpc.admin;

public final class AdminGrpcServerOuter {
  private AdminGrpcServerOuter() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_acb_relayer_admin_AdminRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_acb_relayer_admin_AdminRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_acb_relayer_admin_AdminResponse_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_acb_relayer_admin_AdminResponse_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\017admingrpc.proto\022\021acb.relayer.admin\"G\n\014" +
      "AdminRequest\022\030\n\020commandNamespace\030\001 \001(\t\022\017" +
      "\n\007command\030\002 \001(\t\022\014\n\004args\030\003 \003(\t\"B\n\rAdminRe" +
      "sponse\022\017\n\007success\030\001 \001(\010\022\016\n\006result\030\002 \001(\t\022" +
      "\020\n\010errorMsg\030\003 \001(\t2k\n\024AdministratorServic" +
      "e\022S\n\014adminRequest\022\037.acb.relayer.admin.Ad" +
      "minRequest\032 .acb.relayer.admin.AdminResp" +
      "onse\"\000B^\n2com.alipay.antchain.bridge.rel" +
      "ayer.core.grpc.adminB\024AdminGrpcServerOut" +
      "erP\001\242\002\017AdminGrpcServerb\006proto3"
    };
    descriptor = com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        });
    internal_static_acb_relayer_admin_AdminRequest_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_acb_relayer_admin_AdminRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_acb_relayer_admin_AdminRequest_descriptor,
        new java.lang.String[] { "CommandNamespace", "Command", "Args", });
    internal_static_acb_relayer_admin_AdminResponse_descriptor =
      getDescriptor().getMessageTypes().get(1);
    internal_static_acb_relayer_admin_AdminResponse_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_acb_relayer_admin_AdminResponse_descriptor,
        new java.lang.String[] { "Success", "Result", "ErrorMsg", });
  }

  // @@protoc_insertion_point(outer_class_scope)
}
