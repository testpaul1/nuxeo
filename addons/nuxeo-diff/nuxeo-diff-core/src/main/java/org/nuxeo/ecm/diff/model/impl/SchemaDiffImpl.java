/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     ataillefer
 */
package org.nuxeo.ecm.diff.model.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.diff.model.PropertyDiff;
import org.nuxeo.ecm.diff.model.SchemaDiff;

/**
 * Implementation of SchemaDiff using a HashMap.
 *
 * @author <a href="mailto:ataillefer@nuxeo.com">Antoine Taillefer</a>
 */
public class SchemaDiffImpl implements SchemaDiff {

    private static final long serialVersionUID = -5117078340766697371L;

    /**
     * Map holding the schema diff.
     * <p>
     * Keys are field names. Values represent the difference between the left doc and the right doc for the given field.
     */
    private Map<String, PropertyDiff> schemaDiff;

    /**
     * Instantiates a new schema diff impl.
     */
    public SchemaDiffImpl() {
        schemaDiff = new HashMap<>();
    }

    @Override
    public Map<String, PropertyDiff> getSchemaDiff() {
        return schemaDiff;
    }

    @Override
    public int getFieldCount() {
        return schemaDiff.size();
    }

    @Override
    public List<String> getFieldNames() {
        return new ArrayList<>(schemaDiff.keySet());
    }

    @Override
    public PropertyDiff getFieldDiff(String field) {
        return schemaDiff.get(field);
    }

    @Override
    public PropertyDiff putFieldDiff(String field, PropertyDiff fieldDiff) {
        return schemaDiff.put(field, fieldDiff);
    }

}