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

package com.jaxio.celerio.configuration.support;

import com.jaxio.celerio.configuration.eclipse.Profiles;
import com.jaxio.celerio.configuration.eclipse.Setting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.oxm.Marshaller;
import org.springframework.oxm.Unmarshaller;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class EclipseProfilesLoader extends AbstractJibxLoader<Profiles> {

    private final Marshaller marshaller;

    @Autowired
    public EclipseProfilesLoader(@Qualifier("eclipseProfilesMarshaller") Marshaller marshaller) {
        this.marshaller = marshaller;
    }

    public Marshaller getMarshaller() {
        return marshaller;
    }

    public Unmarshaller getUnmarshaller() {
        return (Unmarshaller) marshaller;
    }

    public List<Setting> loadSettingsFromEclipseFile(File eclipseFile) throws IOException {
        Profiles profiles = load(eclipseFile);
        if (!profiles.getProfiles().isEmpty()) {
            return profiles.getProfiles().iterator().next().getSettings();
        }

        return new ArrayList<Setting>();
    }
}
