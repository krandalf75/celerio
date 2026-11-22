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

package com.jaxio.celerio.support;

import com.jaxio.celerio.util.StringUtil;
import lombok.AccessLevel;
import lombok.Getter;

@Getter
public class AccessorNamer extends AbstractNamer {
    @Getter(AccessLevel.NONE)
    private final String propertyName;
    private String vars;
    private final String type;
    private final String packageName;

    public AccessorNamer(Namer namer) {
        this.propertyName = StringUtil.escape(namer.getVar());
        this.type = namer.getType();
        this.packageName = namer.getPackageName();
    }

    public AccessorNamer(Namer namer, String propertyName) {
        this.propertyName = StringUtil.escape(propertyName);
        this.type = namer.getType();
        this.packageName = namer.getPackageName();
    }

    public AccessorNamer(Namer namer, String propertyName, String vars) {
        this.propertyName = StringUtil.escape(propertyName);
        this.vars = StringUtil.escape(vars);
        this.type = namer.getType();
        this.packageName = namer.getPackageName();
    }

    // used for tests
    public AccessorNamer(String propertyName, String type, String packageName) {
        this.propertyName = StringUtil.escape(propertyName);
        this.type = type;
        this.packageName = packageName;
    }

    @Override
    public String getVar() {
        return propertyName;
    }

    @Override
    public String getVars() {
        return vars != null ? vars : super.getVars();
    }
}