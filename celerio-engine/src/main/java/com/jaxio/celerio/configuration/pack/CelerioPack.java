/*
 * Copyright 2015 JAXIO http://www.jaxio.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jaxio.celerio.configuration.pack;

import com.jaxio.celerio.configuration.CelerioTemplateContext;
import lombok.Data;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@Data
@XmlRootElement(name = "celerioPack", namespace = "http://www.jaxio.com/schema/celerio")
@XmlAccessorType(XmlAccessType.FIELD)
public class CelerioPack {
    @XmlElement(name = "packName")
    private Value packName;
    @XmlElement(name = "packDescription")
    private Value packDescription;
    @XmlElement(name = "packDescription2")
    private Value packDescription2;
    @XmlElement(name = "packCommand")
    private Value packCommand;
    @XmlElement(name = "packCommandHelp")
    private Value packCommandHelp;
    @XmlElement(name = "projectLink")
    private Value projectLink;
    @XmlElement(name = "celerioTemplateContext")
    private CelerioTemplateContext celerioTemplateContext;
}
