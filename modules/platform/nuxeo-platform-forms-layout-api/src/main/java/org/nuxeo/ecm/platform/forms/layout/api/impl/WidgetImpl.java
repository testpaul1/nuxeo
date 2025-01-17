/*
 * (C) Copyright 2006-2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     <a href="mailto:at@nuxeo.com">Anahide Tchertchian</a>
 */
package org.nuxeo.ecm.platform.forms.layout.api.impl;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.platform.forms.layout.api.FieldDefinition;
import org.nuxeo.ecm.platform.forms.layout.api.RenderingInfo;
import org.nuxeo.ecm.platform.forms.layout.api.Widget;
import org.nuxeo.ecm.platform.forms.layout.api.WidgetDefinition;
import org.nuxeo.ecm.platform.forms.layout.api.WidgetSelectOption;

/**
 * Implementation for widgets.
 *
 * @author <a href="mailto:at@nuxeo.com">Anahide Tchertchian</a>
 */
public class WidgetImpl implements Widget {

    private static final long serialVersionUID = 1L;

    protected String id;

    protected String layoutName;

    protected String name;

    protected String mode;

    protected String type;

    protected String typeCategory;

    protected FieldDefinition[] fields;

    protected String helpLabel;

    protected Widget[] subWidgets;

    protected Map<String, Serializable> properties;

    protected Map<String, Serializable> controls;

    protected boolean required = false;

    protected String valueName;

    protected String label;

    protected boolean translated = false;

    protected int level = 0;

    protected WidgetSelectOption[] selectOptions;

    protected List<RenderingInfo> renderingInfos;

    protected String definitionId;

    protected boolean dynamic = false;

    protected boolean global = false;

    protected WidgetDefinition definition;

    // needed by GWT serialization
    protected WidgetImpl() {
        super();
    }

    // BBB
    public WidgetImpl(String layoutName, String name, String mode, String type, String valueName,
            FieldDefinition[] fields, String label, String helpLabel, boolean translated,
            Map<String, Serializable> properties, boolean required, Widget[] subWidgets, int level,
            WidgetSelectOption[] selectOptions, String definitionId) {
        this(layoutName, name, mode, type, valueName, fields, label, helpLabel, translated, properties, required,
                subWidgets, level, selectOptions, definitionId, null);
    }

    /**
     * @since 5.5
     */
    // BBB
    public WidgetImpl(String layoutName, String name, String mode, String type, String valueName,
            FieldDefinition[] fields, String label, String helpLabel, boolean translated,
            Map<String, Serializable> properties, boolean required, Widget[] subWidgets, int level,
            WidgetSelectOption[] selectOptions, String definitionId, List<RenderingInfo> renderingInfos) {
        this.layoutName = layoutName;
        this.name = name;
        this.mode = mode;
        this.type = type;
        this.valueName = valueName;
        this.fields = fields;
        this.label = label;
        this.helpLabel = helpLabel;
        this.translated = translated;
        this.properties = properties;
        this.required = required;
        this.subWidgets = subWidgets;
        this.level = level;
        this.selectOptions = selectOptions;
        this.definitionId = definitionId;
        this.renderingInfos = renderingInfos;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getTagConfigId() {
        StringBuilder builder = new StringBuilder();
        builder.append(definitionId).append(";");
        builder.append(layoutName).append(";");
        builder.append(mode).append(";");
        builder.append(level).append(";");

        Integer intValue = Integer.valueOf(builder.toString().hashCode());
        return intValue.toString();
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getLayoutName() {
        return layoutName;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getMode() {
        return mode;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getTypeCategory() {
        return typeCategory;
    }

    public void setTypeCategory(String typeCategory) {
        this.typeCategory = typeCategory;
    }

    @Override
    public String getLabel() {
        if (label == null) {
            // compute default label name
            label = "label.widget." + layoutName + "." + name;
        }
        return label;
    }

    @Override
    public String getHelpLabel() {
        return helpLabel;
    }

    @Override
    public boolean isTranslated() {
        return translated;
    }

    @Override
    public boolean isHandlingLabels() {
        Map<String, Serializable> controls = getControls();
        if (controls != null && controls.containsKey("handleLabels")) {
            Serializable handling = controls.get("handleLabels");
            if (handling != null) {
                return Boolean.parseBoolean(handling.toString());
            }
        }
        return false;
    }

    @Override
    public Map<String, Serializable> getProperties() {
        if (properties == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(properties);
    }

    @Override
    public Serializable getProperty(String name) {
        if (properties != null) {
            return properties.get(name);
        }
        return null;
    }

    @Override
    public void setProperty(String name, Serializable value) {
        if (properties == null) {
            properties = new HashMap<>();
        }
        properties.put(name, value);
    }

    @Override
    public Map<String, Serializable> getControls() {
        if (controls == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(controls);
    }

    @Override
    public Serializable getControl(String name) {
        if (controls != null) {
            return controls.get(name);
        }
        return null;
    }

    @Override
    public void setControl(String name, Serializable value) {
        if (controls == null) {
            controls = new HashMap<>();
        }
        controls.put(name, value);
    }

    /**
     * @since 6.0
     */
    public void setControls(Map<String, Serializable> controls) {
        this.controls = controls;
    }

    @Override
    public boolean isRequired() {
        return required;
    }

    @Override
    public FieldDefinition[] getFieldDefinitions() {
        return fields;
    }

    @Override
    public Widget[] getSubWidgets() {
        return subWidgets;
    }

    @Override
    public String getValueName() {
        return valueName;
    }

    @Override
    public void setValueName(String valueName) {
        this.valueName = valueName;
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public WidgetSelectOption[] getSelectOptions() {
        return selectOptions;
    }

    @Override
    public List<RenderingInfo> getRenderingInfos() {
        return renderingInfos;
    }

    @Override
    public boolean isDynamic() {
        return dynamic;
    }

    public void setDynamic(boolean dynamic) {
        this.dynamic = dynamic;
    }

    @Override
    public boolean isGlobal() {
        return global;
    }

    public void setGlobal(boolean global) {
        this.global = global;
    }

    @Override
    public WidgetDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(WidgetDefinition definition) {
        this.definition = definition;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        sb.append("WidgetImpl");
        sb.append(" {");
        sb.append(" name=");
        sb.append(name);
        sb.append(", layoutName=");
        sb.append(layoutName);
        sb.append(", id=");
        sb.append(id);
        sb.append(", mode=");
        sb.append(mode);
        sb.append(", type=");
        sb.append(type);
        sb.append(", label=");
        sb.append(label);
        sb.append(", helpLabel=");
        sb.append(helpLabel);
        sb.append(", translated=");
        sb.append(translated);
        sb.append(", required=");
        sb.append(required);
        sb.append(", properties=");
        sb.append(properties);
        sb.append(", controls=");
        sb.append(controls);
        sb.append(", valueName=");
        sb.append(valueName);
        sb.append(", level=");
        sb.append(level);
        sb.append('}');

        return sb.toString();
    }

}
