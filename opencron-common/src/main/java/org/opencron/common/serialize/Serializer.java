/**
 * Copyright (c) 2015 The Opencron Project
 * <p>
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.opencron.common.serialize;

import org.opencron.common.ext.SPI;

import java.io.IOException;
/**
 * Serialization. (SPI, Singleton, ThreadSafe)
 */
@SPI
public interface Serializer {

    /**
     * get content type id
     *
     * @return content type id
     */
    byte getContentTypeId();

    /**
     * get content type
     *
     * @return content type
     */
    String getContentType();

    /**
     * create serializer
     *
     * @return serializer
     * @throws IOException
     */
    byte[] serialize(Object object) throws IOException;

    /**
     * create deserializer
     *
     * @param bytes
     * @return deserializer
     * @throws IOException
     */
    <T>T deserialize(byte[] bytes,Class<T> clazz) throws IOException;

}