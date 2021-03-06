/*
 * Copyright 2014, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.grpc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Definition of a service to be exposed via a Server. */
public final class ServerServiceDefinition {
  /** Convenience that constructs a {@link ServiceDescriptor} simultaneously. */
  public static Builder builder(String serviceName) {
    return new Builder(serviceName);
  }

  public static Builder builder(ServiceDescriptor serviceDescriptor) {
    return new Builder(serviceDescriptor);
  }

  private final ServiceDescriptor serviceDescriptor;
  private final Map<String, ServerMethodDefinition<?, ?>> methods;

  private ServerServiceDefinition(
      ServiceDescriptor serviceDescriptor, Map<String, ServerMethodDefinition<?, ?>> methods) {
    this.serviceDescriptor = checkNotNull(serviceDescriptor, "serviceDescriptor");
    this.methods =
        Collections.unmodifiableMap(new HashMap<String, ServerMethodDefinition<?, ?>>(methods));
  }

  /**
   * The descriptor for the service.
   */
  public ServiceDescriptor getServiceDescriptor() {
    return serviceDescriptor;
  }

  public Collection<ServerMethodDefinition<?, ?>> getMethods() {
    return methods.values();
  }

  /**
   * Look up a method by its fully qualified name.
   *
   * @param methodName the fully qualified name without leading slash. E.g., "com.foo.Foo/Bar"
   */
  @Internal
  public ServerMethodDefinition<?, ?> getMethod(String methodName) {
    return methods.get(methodName);
  }

  /**
   * Builder for constructing Service instances.
   */
  public static final class Builder {
    private final String serviceName;
    private final ServiceDescriptor serviceDescriptor;
    private final Map<String, ServerMethodDefinition<?, ?>> methods =
        new HashMap<String, ServerMethodDefinition<?, ?>>();

    private Builder(String serviceName) {
      this.serviceName = checkNotNull(serviceName, "serviceName");
      this.serviceDescriptor = null;
    }

    private Builder(ServiceDescriptor serviceDescriptor) {
      this.serviceDescriptor = checkNotNull(serviceDescriptor, "serviceDescriptor");
      this.serviceName = serviceDescriptor.getName();
    }

    /**
     * Add a method to be supported by the service.
     *
     * @param method the {@link MethodDescriptor} of this method.
     * @param handler handler for incoming calls
     */
    public <ReqT, RespT> Builder addMethod(
        MethodDescriptor<ReqT, RespT> method, ServerCallHandler<ReqT, RespT> handler) {
      return addMethod(ServerMethodDefinition.create(
          checkNotNull(method, "method must not be null"),
          checkNotNull(handler, "handler must not be null")));
    }

    /** Add a method to be supported by the service. */
    public <ReqT, RespT> Builder addMethod(ServerMethodDefinition<ReqT, RespT> def) {
      MethodDescriptor<ReqT, RespT> method = def.getMethodDescriptor();
      checkArgument(
          serviceName.equals(MethodDescriptor.extractFullServiceName(method.getFullMethodName())),
          "Method name should be prefixed with service name and separated with '/'. "
                  + "Expected service name: '%s'. Actual fully qualifed method name: '%s'.",
          serviceName, method.getFullMethodName());
      String name = method.getFullMethodName();
      checkState(!methods.containsKey(name), "Method by same name already registered: %s", name);
      methods.put(name, def);
      return this;
    }

    /**
     * Construct new ServerServiceDefinition.
     */
    public ServerServiceDefinition build() {
      ServiceDescriptor serviceDescriptor = this.serviceDescriptor;
      if (serviceDescriptor == null) {
        List<MethodDescriptor<?, ?>> methodDescriptors
            = new ArrayList<MethodDescriptor<?, ?>>(methods.size());
        for (ServerMethodDefinition<?, ?> serverMethod : methods.values()) {
          methodDescriptors.add(serverMethod.getMethodDescriptor());
        }
        serviceDescriptor = new ServiceDescriptor(serviceName, methodDescriptors);
      }
      Map<String, ServerMethodDefinition<?, ?>> tmpMethods =
          new HashMap<String, ServerMethodDefinition<?, ?>>(methods);
      for (MethodDescriptor<?, ?> descriptorMethod : serviceDescriptor.getMethods()) {
        ServerMethodDefinition<?, ?> removed = tmpMethods.remove(
            descriptorMethod.getFullMethodName());
        if (removed == null) {
          throw new IllegalStateException(
              "No method bound for descriptor entry " + descriptorMethod.getFullMethodName());
        }
        if (removed.getMethodDescriptor() != descriptorMethod) {
          throw new IllegalStateException(
              "Bound method for " + descriptorMethod.getFullMethodName()
                  + " not same instance as method in service descriptor");
        }
      }
      if (tmpMethods.size() > 0) {
        throw new IllegalStateException(
            "No entry in descriptor matching bound method "
                + tmpMethods.values().iterator().next().getMethodDescriptor().getFullMethodName());
      }
      return new ServerServiceDefinition(serviceDescriptor, methods);
    }
  }
}
