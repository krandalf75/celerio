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

package com.jaxio.celerio.factory.conventions;

import com.jaxio.celerio.model.Attribute;
import com.jaxio.celerio.model.Entity;
import com.jaxio.celerio.model.support.AuditEntityAttribute;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.collect.Lists.newArrayList;

@Slf4j
public class AuditEntityConvention {

    private static final String FOUND_ON = " found on ";
    private final Iterable<String> creationAuthor = newArrayList("creationAuthor", "creationBy", "creePar");
    private final Iterable<String> creationDate = newArrayList("creationDate", "dateCreation");
    private final Iterable<String> lastModificationAuthor = newArrayList("lastModificationAuthor", "lastModificationAt", "derniereModificationPar", "modifiePar");
    private final Iterable<String> lastModificationDate = newArrayList("lastModificationDate", "dateDerniereModification", "derniereModification");
    @Getter
    private final AuditEntityAttribute auditEntityAttribute;
    private final Entity e;
    @Getter
    private final boolean audited;

    @SuppressWarnings("java:S6832") // The model creates this helper directly for each entity or attribute.
    public AuditEntityConvention(Entity e) {
        this.e = e;
        auditEntityAttribute = attributes(e);
        this.audited = buildIsAudited();
    }

    private boolean buildIsAudited() {
        boolean supportsAudit = auditEntityAttribute.hasAttribute();
        if (supportsAudit) {
            log.info(e.getName() + ": assumed by convention to accept audit events");
        }
        return supportsAudit;
    }

    /**
     * Is this entity the 'audit log' entity?
     */
    private AuditEntityAttribute attributes(Entity e) {
        AuditEntityAttribute auditEntityAttributes = new AuditEntityAttribute();
        matchCreationAuthor(e, auditEntityAttributes);
        matchCreationDate(e, auditEntityAttributes);
        matchLastModificationAuthor(e, auditEntityAttributes);
        matchLastModificationDate(e, auditEntityAttributes);
        return auditEntityAttributes;
    }

    private void matchCreationAuthor(Entity e, AuditEntityAttribute auditEntityAttribute) {
        for (Attribute a : e.getAttributes().getList()) {
            if (a.isString() && match(a.getVar(), creationAuthor)) {
                auditEntityAttribute.setCreationAuthor(a);
                log.debug("'creationAuthor' candidate: " + a.getVar() + FOUND_ON + e.getName());
                return;
            }
        }
    }

    private void matchCreationDate(Entity e, AuditEntityAttribute auditEntityAttribute) {
        for (Attribute a : e.getAttributes().getList()) {
            if (a.isDate() && match(a.getVar(), creationDate)) {
                auditEntityAttribute.setCreationDate(a);
                log.debug("'creationDate' candidate: " + a.getVar() + FOUND_ON + e.getName());
                return;
            }
        }
    }

    private void matchLastModificationAuthor(Entity e, AuditEntityAttribute auditEntityAttribute) {
        for (Attribute a : e.getAttributes().getList()) {
            if (a.isString() && match(a.getVar(), lastModificationAuthor)) {
                auditEntityAttribute.setLastModificationAuthor(a);
                log.debug("'lastModificationAuthor' candidate: " + a.getVar() + FOUND_ON + e.getName());
                return;
            }
        }
    }

    private void matchLastModificationDate(Entity e, AuditEntityAttribute auditEntityAttribute) {
        for (Attribute a : e.getAttributes().getList()) {
            if (a.isDate() && match(a.getVar(), lastModificationDate)) {
                auditEntityAttribute.setLastModificationDate(a);
                log.debug("'lastModificationDate' candidate: " + a.getVar() + FOUND_ON + e.getName());
                return;
            }
        }
    }

    private boolean match(String target, Iterable<String> candidates) {
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }
}
