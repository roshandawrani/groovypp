/*
 * Copyright 2009-2010 MBTE Sweden AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package groovy.channels

import java.util.concurrent.Executor

class Channels {
    /**
    * Utility method to create a channel from closure.
    */
    static <T> MessageChannel<T> channel(MessageChannel<T> channel) {
        channel
    }

    /**
    * Utility method to create a channel from closure.
    */
    static <T> MessageChannel<T> fairChannel(Executor self, FairExecutingChannel<T> channel) {
        channel.executor = self
        channel
    }

    /**
    * Utility method to create a channel from closure.
    */
    static <T> MessageChannel<T> directChannel(Object ignore, MessageChannel<T> channel) {
        channel
    }

    /**
    * Utility method to create a channel from closure.
    */
    static <T> MessageChannel<T> threadChannel(Executor ignore, MessageChannel<T> channel) {
        channel
    }
}