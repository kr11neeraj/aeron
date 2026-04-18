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

import net.bytebuddy.asm.Advice;

import java.util.Arrays;

/**
 * Generic advice that can be reused across business methods.
 */
public final class BusinessMethodLoggingAdvice
{
    private BusinessMethodLoggingAdvice()
    {
    }

    @Advice.OnMethodEnter
    static long onEnter(
        @Advice.Origin("#t.#m") final String methodName,
        @Advice.AllArguments final Object[] arguments)
    {
        AgentLogRecorder.record("enter " + methodName + " args=" + Arrays.toString(arguments));
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    static void onExit(
        @Advice.Origin("#t.#m") final String methodName,
        @Advice.Enter final long startNs,
        @Advice.Return(typing = net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC)
        final Object result,
        @Advice.Thrown final Throwable throwable)
    {
        final long elapsedNs = System.nanoTime() - startNs;
        if (null == throwable)
        {
            AgentLogRecorder.record("exit " + methodName + " result=" + result + " elapsedNs=" + elapsedNs);
        }
        else
        {
            AgentLogRecorder.record("exit " + methodName + " error=" + throwable + " elapsedNs=" + elapsedNs);
        }
    }
}
