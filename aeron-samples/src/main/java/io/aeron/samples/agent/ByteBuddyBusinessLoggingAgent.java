/*
 * Copyright 2014-2025 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.aeron.samples.agent;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.dynamic.scaffold.TypeValidation;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.asm.Advice.to;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Reusable Java agent for business-method logging. Change target class and method through
 * system properties rather than copying the agent.
 */
public final class ByteBuddyBusinessLoggingAgent
{
    private ByteBuddyBusinessLoggingAgent()
    {
    }

    public static void premain(final String agentArgs, final Instrumentation instrumentation)
    {
        final AgentLoggingConfiguration configuration = AgentLoggingConfiguration.fromSystemProperties();

        AgentLogRecorder.start();
        AgentLogRecorder.record(
            "installing agent for class=" + configuration.targetClassName() +
            ", method=" + configuration.targetMethodName());
        Runtime.getRuntime().addShutdownHook(new Thread(AgentLogRecorder::stop, "sample-agent-shutdown"));

        new AgentBuilder.Default(new ByteBuddy().with(TypeValidation.DISABLED))
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(AgentBuilder.RedefinitionStrategy.DiscoveryStrategy.Reiterating.INSTANCE)
            .with(new AgentLoggingListener())
            .type(named(configuration.targetClassName()))
            .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                builder.visit(to(BusinessMethodLoggingAdvice.class).on(named(configuration.targetMethodName()))))
            .installOn(instrumentation);
    }
}
