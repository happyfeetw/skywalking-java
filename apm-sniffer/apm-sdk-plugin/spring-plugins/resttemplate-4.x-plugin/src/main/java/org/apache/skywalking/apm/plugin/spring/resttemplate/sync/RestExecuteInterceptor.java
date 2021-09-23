/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.plugin.spring.resttemplate.sync;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URI;
import java.util.logging.Logger;

import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.spring.resttemplate.helper.RestTemplateRuntimeContextHelper;
import org.springframework.http.HttpMethod;

public class RestExecuteInterceptor implements InstanceMethodsAroundInterceptor {
    private static final ILog LOGGER = LogManager.getLogger(RestExecuteInterceptor.class);
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        final URI requestURL = (URI) allArguments[0];
        final HttpMethod httpMethod = (HttpMethod) allArguments[1];
        final ContextCarrier contextCarrier = new ContextCarrier();

        String remotePeer = requestURL.getHost() + ":" + (requestURL.getPort() > 0 ? requestURL.getPort() : "https".equalsIgnoreCase(requestURL
            .getScheme()) ? 443 : 80);
        String formatURIPath = requestURL.getPath();
        AbstractSpan span = ContextManager.createExitSpan(formatURIPath, contextCarrier, remotePeer);

        span.setComponent(ComponentsDefine.SPRING_REST_TEMPLATE);
        Tags.URL.set(span, requestURL.getScheme() + "://" + requestURL.getHost() +
                (requestURL.getPort() > 0 ? ":" + requestURL.getPort() : "") + requestURL.getPath());
        Tags.HTTP.METHOD.set(span, httpMethod.toString());
        SpanLayer.asHttp(span);

        RestTemplateRuntimeContextHelper.addContextCarrier(contextCarrier);

        String path = requestURL.getPath();
        LOGGER.warn("Invoke URI: {}", path);
        if (path.startsWith("/api/") || path.startsWith("/inter-api/") || path.startsWith("/openapi/")) {
            InetAddress local = InetAddress.getLocalHost();
            String localIp = local.getHostAddress();
            String localHostName = local.getHostName();

            String remoteURL = requestURL.toURL().toString();
            String remoteAddr = requestURL.getHost();
            String requestMethod = httpMethod.name();
            LOGGER.warn("local_hostname:{} local_id: {} -> remote_addr: {} remote_url: {} {}",
                    localHostName, localIp, remoteAddr, requestMethod, remoteURL);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        RestTemplateRuntimeContextHelper.cleanContextCarrier();
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
    }
}
