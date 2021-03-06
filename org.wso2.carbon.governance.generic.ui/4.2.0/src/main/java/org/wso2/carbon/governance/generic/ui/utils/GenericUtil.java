/*
*  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.governance.generic.ui.utils;

import org.apache.axiom.om.*;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axiom.om.xpath.AXIOMXPath;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jaxen.JaxenException;
import org.wso2.carbon.governance.api.util.GovernanceArtifactConfiguration;
import org.wso2.carbon.governance.api.util.GovernanceUtils;
import org.wso2.carbon.registry.core.ActionConstants;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.utils.RegistryUtils;
import org.wso2.carbon.registry.resource.ui.Utils;
import org.wso2.carbon.registry.resource.ui.clients.ResourceServiceClient;
import org.wso2.carbon.registry.ws.client.registry.WSRegistryServiceClient;
import org.wso2.carbon.ui.CarbonUIUtil;
import org.wso2.carbon.ui.MenuAdminClient;
import org.wso2.carbon.ui.deployment.ComponentBuilder;
import org.wso2.carbon.ui.deployment.beans.Component;
import org.wso2.carbon.ui.deployment.beans.Menu;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.utils.ServerConstants;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.*;

public class GenericUtil {
    private static final Log log = LogFactory.getLog(GenericUtil.class);
    private static final String DEFAULT_LIFECYCLE_GENERATOR_CLASS
            = "org.wso2.carbon.governance.generic.ui.utils.LifecycleListPopulator";

    public static void buildMenuItems(HttpServletRequest request, String s, String s1, String s2) {
        int menuOrder = 50;
        if(CarbonUIUtil.isUserAuthorized(request, "/permission/admin/manage/resources/ws-api")){
            HttpSession session = request.getSession();
            String cookie =
                    (String) session.getAttribute(ServerConstants.ADMIN_SERVICE_COOKIE);
            try {
                WSRegistryServiceClient registry = new WSRegistryServiceClient(s2, cookie);
                List<GovernanceArtifactConfiguration> configurations =
                        GovernanceUtils.findGovernanceArtifactConfigurations(registry);
                Map<String, String> customAddUIMap = new LinkedHashMap<String, String>();
                Map<String, String> customViewUIMap = new LinkedHashMap<String, String>();
                List<Menu> userCustomMenuItemsList = new LinkedList<Menu>();
                for (GovernanceArtifactConfiguration configuration : configurations) {
                    Component component = new Component();
                    OMElement uiConfigurations = configuration.getUIConfigurations();
                    String key = configuration.getKey();
                    String configurationPath = RegistryConstants.CONFIG_REGISTRY_BASE_PATH +
                            RegistryConstants.GOVERNANCE_COMPONENT_PATH +
                            "/configuration/";
                    String layoutStoragePath = configurationPath
                            + key;
                    RealmService realmService = registry.getRegistryContext().getRealmService();
                    if (realmService.getTenantUserRealm(realmService.getTenantManager().getTenantId(s1))
                            .getAuthorizationManager().isUserAuthorized(s, configurationPath, ActionConstants.PUT)
                            || registry.resourceExists(layoutStoragePath)) {
                        List<Menu> menuList = component.getMenusList();
                        if (uiConfigurations != null) {
                            ComponentBuilder
                                    .processMenus("artifactType", uiConfigurations, component);
                            ComponentBuilder.processCustomUIs(uiConfigurations, component);
                        }
                        if (menuList.size() == 0) {
                            // if no menu definitions were present, define the default ones.
                            menuOrder = buildMenuList(request, configuration, menuList, key, menuOrder);
                        }
                        userCustomMenuItemsList.addAll(menuList);
                        customAddUIMap.putAll(component.getCustomAddUIMap());
                        Map<String, String> viewUIMap =
                                component.getCustomViewUIMap();
                        if (viewUIMap.isEmpty()) {
                            // if no custom UI definitions were present, define the default.
                            buildViewUI(configuration, viewUIMap, key);
                        }
                        customViewUIMap.putAll(viewUIMap);
                        OMElement layout = configuration.getContentDefinition();
                        if (layout != null && !registry.resourceExists(layoutStoragePath)) {
                            Resource resource = registry.newResource();
                            resource.setContent(RegistryUtils.encodeString(layout.toString()));
                            resource.setMediaType("application/xml");
                            registry.put(layoutStoragePath, resource);
                        }
                    }
                }
                session.setAttribute(MenuAdminClient.USER_CUSTOM_MENU_ITEMS,
                        userCustomMenuItemsList.toArray(
                                new Menu[userCustomMenuItemsList.size()]));
                session.setAttribute("customAddUI", customAddUIMap);
                session.setAttribute("customViewUI",customViewUIMap);
            } catch (RegistryException e) {
                log.error("unable to create connection to registry");
            } catch (org.wso2.carbon.user.api.UserStoreException e) {
                log.error("unable to realm service");
            }
        }
    }

    private static void buildViewUI(GovernanceArtifactConfiguration configuration,
                             Map<String, String> viewUIMap, String key) {
        String singularLabel = configuration.getSingularLabel();
        String pluralLabel = configuration.getPluralLabel();

        String lifecycleAttribute = key + "Lifecycle_lifecycleName";

        lifecycleAttribute = BuilLifecycleAttribute(configuration, DEFAULT_LIFECYCLE_GENERATOR_CLASS, lifecycleAttribute);

        if (singularLabel == null || pluralLabel == null) {
            log.error("The singular label and plural label have not " +
                    "been defined for the artifact type: " + key);
        } else {
            String contentURL = configuration.getContentURL();
            if (contentURL != null) {
                if (!contentURL.toLowerCase().equals("default")) {
                    viewUIMap.put(configuration.getMediaType(), contentURL);
                }
            } else {
                String path = "../generic/edit_ajaxprocessor.jsp?hideEditView=true&key=" + key +
                        "&lifecycleAttribute=" + lifecycleAttribute +"&add_edit_breadcrumb=" +
                        singularLabel + "&add_edit_region=region3&add_edit_item=governance_add_" +
                        key + "_menu&breadcrumb=" + singularLabel;
                viewUIMap.put(configuration.getMediaType(), path);
            }
        }
    }

    /*
    * This method is used to capture the lifecycle attribute from the configuration.
    *
    * expected configuration elements are
    *
    * <field type="options">
            <name label="Lifecycle Name" >Lifecycle Name</name>
            <values class="org.wso2.carbon.governance.generic.ui.utils.LifecycleListPopulator"/>
        </field>
    *
    * or
    *
    *  <field type="options">
            <name label="Lifecycle Name" >Lifecycle Name</name>
            <values class="com.foo.bar.LifecycleListPopulator" isLifecycle="true"/>
        </field>
    *  */
    private static String BuilLifecycleAttribute(GovernanceArtifactConfiguration configuration,
                                          String defaultLifecycleGeneratorClass, String lifecycleAttribute) {
        try {
//            This part checks whether the user has given a lifecycle populates.
//            If not, then we check whether there is an attribute called, "isLifecycle"
//            This attribute will identify the lifecycle attribute from the configuration.
            OMElement configurationElement = configuration.getContentDefinition();
            String xpathExpression = "//@class";

            AXIOMXPath xpath = new AXIOMXPath(xpathExpression);
            List resultNodes = xpath.selectNodes(configurationElement);

            if (resultNodes != null && resultNodes.size() > 0) {
                String lifecycleParentName = null;
                String lifecycleName = null;

                for (Object resultNode : resultNodes) {
                    OMElement parentElement = ((OMAttribute)resultNode).getOwner();
                    if(parentElement.getAttributeValue(new QName("class")).equals(defaultLifecycleGeneratorClass)){
                        Iterator childrenIterator = parentElement.getParent().getChildrenWithLocalName("name");
                        while (childrenIterator.hasNext()) {
                            OMElement next = (OMElement) childrenIterator.next();
                            lifecycleName = next.getAttributeValue(new QName("label"));
                        }
                        OMElement rootElement = (OMElement) ((OMElement) parentElement.getParent()).getParent();
                        lifecycleParentName = rootElement.getAttributeValue(new QName("name"));
                        break;
                    }else if(parentElement.getAttributeValue(new QName("isLifecycle")) != null && parentElement.getAttributeValue(new QName("isLifecycle")).equals("true")){
                        Iterator childrenIterator = parentElement.getParent().getChildrenWithLocalName("name");
                        while (childrenIterator.hasNext()) {
                            OMElement next = (OMElement) childrenIterator.next();
                            lifecycleName = next.getAttributeValue(new QName("label"));
                        }
                        OMElement rootElement = (OMElement) ((OMElement) parentElement.getParent()).getParent();
                        lifecycleParentName = rootElement.getAttributeValue(new QName("name"));
                        break;
                    }
                }
                if (lifecycleParentName != null && lifecycleName != null) {
                    return convertName(lifecycleParentName.split(" "))
                            + "_" + convertName(lifecycleName.split(" "));
                }
            }

        } catch (OMException e) {
            log.error("Governance artifact configuration of configuration key:" + configuration.getKey() + " is invalid", e);
        } catch (JaxenException e) {
            log.error("Error in getting the lifecycle attribute",e);
        }
        return null;
    }

    private static String convertName(String[] nameParts) {
        String convertedName = null;
        //  making widget name camel case

        for (String namePart : nameParts) {
            int i;
            for (i = 0; i < namePart.length(); i++) {
                char c = namePart.charAt(i);
                if (!Character.isLetter(c) || Character.isLowerCase(c)) {
                    break;
                }
            }
            if (namePart.equals(nameParts[0])) {
                namePart = namePart.substring(0, i).toLowerCase() + namePart.substring(i);
            }
            if (convertedName == null) {
                convertedName = namePart;
            } else {
                convertedName += namePart;
            }
        }
        return convertedName;
    }

    private static int buildMenuList(HttpServletRequest request,
                              GovernanceArtifactConfiguration configuration, List<Menu> menuList,
                              String key, int menuOrder) {
        String singularLabel = configuration.getSingularLabel();
        String pluralLabel = configuration.getPluralLabel();
        boolean hasNamespace = configuration.hasNamespace();
        String lifecycleAttribute = key + "Lifecycle_lifecycleName";

        lifecycleAttribute = BuilLifecycleAttribute(configuration, DEFAULT_LIFECYCLE_GENERATOR_CLASS, lifecycleAttribute);

        if (singularLabel == null || pluralLabel == null) {
            log.error("The singular label and plural label have not " +
                    "been defined for the artifact type: " + key);
        } else {
            int iconSet = configuration.getIconSet();
            if (CarbonUIUtil.isUserAuthorized(request,
                    "/permission/admin/manage/resources/govern/" + key + "/add") &&
                    CarbonUIUtil.isUserAuthorized(request,
                            "/permission/admin/manage/resources/browse") &&
                    CarbonUIUtil.isUserAuthorized(request,
                            "/permission/admin/manage/resources/ws-api")) {
                Menu addMenu = new Menu();
                addMenu.setId("governance_add_" + key + "_menu");
                addMenu.setI18nKey(singularLabel);
                addMenu.setParentMenu("add_sub_menu");
                if (configuration.getExtension() == null) {
                    addMenu.setLink("../generic/add_edit.jsp");
                    addMenu.setUrlParameters("key=" + key + "&lifecycleAttribute=" +
                            lifecycleAttribute + "&breadcrumb=" + singularLabel);
                } else {
                    addMenu.setLink("../generic/add_content.jsp");
                    addMenu.setUrlParameters("key=" + key + "&lifecycleAttribute=" +
                            lifecycleAttribute + "&breadcrumb=" + singularLabel + "&mediaType=" +
                            configuration.getMediaType() + "&extension=" +
                            configuration.getExtension() + "&singularLabel=" + singularLabel +
                            "&pluralLabel=" + pluralLabel + "&hasNamespace" + hasNamespace);
                }
                addMenu.setRegion("region3");
                addMenu.setOrder(String.valueOf(menuOrder));
                addMenu.setStyleClass("manage");
                if (iconSet > 0) {
                    addMenu.setIcon("../generic/images/add" + iconSet + ".png");
                } else {
                    addMenu.setIcon("../images/add.gif");
                }
                addMenu.setAllPermissionsRequired(true);
                addMenu.setRequirePermission(
                        new String[]{"/permission/admin/manage/resources/govern/" + key + "/add",
                                "/permission/admin/manage/resources/browse",
                                "/permission/admin/manage/resources/ws-api"});
                menuList.add(addMenu);
            }

            if (CarbonUIUtil.isUserAuthorized(request,
                    "/permission/admin/manage/resources/govern/" + key + "/list") &&
                    CarbonUIUtil.isUserAuthorized(request,
                            "/permission/admin/manage/resources/ws-api")) {
                Menu listMenu = new Menu();
                listMenu.setId("governance_list_" + key + "_menu");
                listMenu.setI18nKey(pluralLabel);
                listMenu.setParentMenu("list_sub_menu");
                if (configuration.getExtension() == null) {
                    listMenu.setLink("../generic/list.jsp");
                    listMenu.setUrlParameters("key=" + key + "&breadcrumb=" + pluralLabel +
                            "&singularLabel=" + singularLabel + "&pluralLabel=" + pluralLabel);
                } else {
                    listMenu.setLink("../generic/list_content.jsp");
                    listMenu.setUrlParameters("key=" + key + "&lifecycleAttribute=" +
                            lifecycleAttribute + "&breadcrumb=" + singularLabel + "&mediaType=" +
                            configuration.getMediaType() + "&singularLabel=" + singularLabel +
                            "&pluralLabel=" + pluralLabel + "&hasNamespace=" + hasNamespace);
                }
                listMenu.setRegion("region3");
                listMenu.setOrder(String.valueOf(menuOrder));
                listMenu.setStyleClass("manage");
                if (iconSet > 0) {
                    listMenu.setIcon("../generic/images/list" + iconSet + ".png");
                } else {
                    listMenu.setIcon("../images/list.gif");
                }
                listMenu.setAllPermissionsRequired(true);
                listMenu.setRequirePermission(
                        new String[]{"/permission/admin/manage/resources/govern/" + key + "/list",
                                "/permission/admin/manage/resources/ws-api"});
                menuList.add(listMenu);
            }
            /*if (CarbonUIUtil.isUserAuthorized(request,
                    "/permission/admin/configure/governance/" + key + "-ui")) {
                Menu configureMenu = new Menu();
                configureMenu.setId("governance_" + key + "_config_menu");
                configureMenu.setI18nKey(pluralLabel);
                configureMenu.setParentMenu("configure_menu");
                configureMenu.setLink("../generic/configure.jsp");
                configureMenu.setUrlParameters("key=" + key + "&breadcrumb=" + pluralLabel +
                        "&add_edit_region=region3&add_edit_item=governance_add_" + key + "_menu" +
                        "&lifecycleAttribute=" + lifecycleAttribute +
                        "&add_edit_breadcrumb=" + singularLabel + "&singularLabel=" + singularLabel +
                        "&pluralLabel=" + pluralLabel);
                configureMenu.setRegion("region1");
                configureMenu.setOrder("40");
                configureMenu.setStyleClass("manage");
                if (iconSet > 0) {
                    configureMenu.setIcon("../generic/images/configure" + iconSet + ".png");
                } else {
                    configureMenu.setIcon("../generic/images/services1.gif");
                }
                configureMenu.setRequirePermission(
                        new String[]{"/permission/admin/configure/governance/" + key + "-ui"});
                menuList.add(configureMenu);
            }*/
            menuOrder++;
        }
        return menuOrder;
    }

    public static String getGreatestChildVersion(ServletConfig config, HttpSession session,
                                                 String path) throws Exception {
        String[] nodes =
                Utils.getSortedChildNodes(
                        new ResourceServiceClient(config, session).getCollectionContent(
                                path));
        String last = "";
        for (String node : nodes) {
            String name = RegistryUtils.getResourceName(node);
            try {
                Integer.parseInt(name);
                last = name;
            } catch (NumberFormatException ignore) {
            }
        }
        return last;
    }

    public static int[] getAdjacentVersions(ServletConfig config, HttpSession session,
                                            String path, int current) throws Exception {
        String[] nodes =
                Utils.getSortedChildNodes(
                        new ResourceServiceClient(config, session).getCollectionContent(path));
        int[] versions = new int[2];
        versions[0] = -1;
        versions[1] = -1;
        int previous = -1;
        for (String node : nodes) {
            String name = RegistryUtils.getResourceName(node);
            try {
                int temp = Integer.parseInt(name);
                if (previous == current) {
                    // The last match was the current version. Therefore, the match is the version
                    // after the current.
                    versions[1] = temp;
                    break;
                }
                if (temp == current) {
                    // The match is the current version. Therefore, the last match was the version
                    // before the current.
                    versions[0] = previous;
                }
                previous = temp;
            } catch (NumberFormatException ignore) {
            }
        }
        return versions;
    }

    public static String getUniqueNameForNamespaceToRedirect(String commonSchemaLocation, String targetNamespace1) {
        String resourcePath;
        String targetNamespace = targetNamespace1.replaceAll("\\s+$", "");
        targetNamespace = targetNamespace.replace("://", RegistryConstants.PATH_SEPARATOR);
        targetNamespace = targetNamespace.replace(".", RegistryConstants.PATH_SEPARATOR);

        if (commonSchemaLocation.endsWith(RegistryConstants.PATH_SEPARATOR)) {
            resourcePath = new StringBuilder()
                    .append(commonSchemaLocation)
                    .append(targetNamespace).toString();
        } else {
            resourcePath = new StringBuilder()
                    .append(commonSchemaLocation)
                    .append(RegistryConstants.PATH_SEPARATOR)
                    .append(targetNamespace).toString();
        }

        if (!targetNamespace.endsWith(RegistryConstants.PATH_SEPARATOR)) {
            resourcePath = new StringBuilder().append(resourcePath).append(RegistryConstants.PATH_SEPARATOR).toString();
        }

        return resourcePath;
    }

    public static String getNamespaceFromContent(OMElement head) {
        OMElement overview = head.getFirstChildWithName(new
                QName("Overview"));
        if (overview != null) {
            return overview.getFirstChildWithName(new QName("Namespace")).getText();
        }
        overview = head.getFirstChildWithName(new
                QName(UIGeneratorConstants.DATA_NAMESPACE, "overview"));
        if (overview != null) {
            return overview.getFirstChildWithName(new QName(UIGeneratorConstants.DATA_NAMESPACE, "namespace")).getText();
        }
        return null;
    }

    public static String getNameFromContent(OMElement head) {
        OMElement overview = head.getFirstChildWithName(new
                QName("Overview"));
        if (overview != null) {
            return overview.getFirstChildWithName(new QName("Name")).getText();
        }
        overview = head.getFirstChildWithName(new
                QName(UIGeneratorConstants.DATA_NAMESPACE, "overview"));
        if (overview != null) {
            return overview.getFirstChildWithName(new QName(UIGeneratorConstants.DATA_NAMESPACE, "name")).getText();
        }
        return null;
    }

    public static OMElement getUIConfiguration(String filePath) {
        InputStream stream = GenericUIGenerator.class.getResourceAsStream(filePath);
        try {
            StAXOMBuilder builder = null;
            OMElement omElement = null;
            try {
                builder = new StAXOMBuilder(stream);
                omElement = builder.getDocumentElement();
            } catch (XMLStreamException e) {
                e.printStackTrace();
            }
            return omElement;
        } finally {
            try {
                stream.close();
            } catch (IOException ignore) {
            }
        }
    }

    public static OMElement addExtraElements(OMElement data, HttpServletRequest request) {
        OMFactory fac = OMAbstractFactory.getOMFactory();
        //adding required fields at the top of the xml which will help to easily read in service side
        OMElement operation = fac.createOMElement("operation", null);
        OMElement currentName = fac.createOMElement("currentName", null);
        OMElement currentNamespace = fac.createOMElement("currentNamespace", null);

        String operationValue = request.getParameter("operation");
        if (operationValue != null) {
            operation.setText(operationValue);
            data.addChild(operation);
        }
        String name = request.getParameter("currentname");
        if (name != null) {
            currentName.setText(name);
            data.addChild(currentName);
        }
        String namespace = request.getParameter("currentnamespace");
        if (namespace != null) {
            currentNamespace.setText(namespace);
            data.addChild(currentNamespace);
        }
        return data;
    }

    public static OMElement loadAddedServiceContent(String xmlContent) throws Exception {
        try {
            XMLStreamReader reader = XMLInputFactory.newInstance().
                    createXMLStreamReader(new StringReader(xmlContent));
            StAXOMBuilder builder = new StAXOMBuilder(reader);
            return builder.getDocumentElement();
        } catch (Exception ex) {
            throw ex;
        }
    }

    public static String getDataElementName(String widgetName) {
        if (widgetName == null || widgetName.length() == 0) {
            return null;
        }
        String[] nameParts = widgetName.split("_");
        String convertedName = null;
        //  making widget name camel case
        for (String namePart : nameParts) {
            int i;
            for (i = 0; i < namePart.length(); i++) {
                char c = namePart.charAt(i);
                if (!Character.isLetter(c) || Character.isLowerCase(c)) {
                    break;
                }
            }
            namePart = namePart.substring(0, i).toLowerCase() + namePart.substring(i);
            if (convertedName == null) {
                convertedName = namePart;
            } else {
                convertedName += "_" + namePart;
            }
        }
        if (convertedName == null) {
            return null;
        }

        return convertedName.replaceAll(" ", "").replaceAll("-", "");
    }

    public static OMElement getChildWithName(OMElement head, String widgetName, String namespace) {
        String adjustedName = getDataElementName(widgetName);
        if (adjustedName == null) {
            return null;
        }
        OMElement child = head.getFirstChildWithName(new QName(namespace, adjustedName));
        if (child == null) {
            // this piece of code is for the backward compatibility
            child = head.getFirstChildWithName(new QName(null,
                    widgetName.replaceAll(" ", "-")));
        }
        return child;
    }
    
    public static List<OMElement> getChildsWithName(OMElement head, String widgetName, String namespace) {
		String adjustedName = getDataElementName(widgetName);
		if (adjustedName == null) {
			return null;
		}
		List<OMElement> list = new ArrayList<OMElement>();
		Iterator headingList = head.getChildrenWithName(new QName(namespace, adjustedName));
		while (headingList.hasNext()) {
			OMElement subheading = (OMElement) headingList.next();
			list.add(subheading);
		}
		return list;
    }

    public static String decorateVersionElement(String version, String basicVersionElement,
                                                String path, String type, String append,
                                                String screenWidth,
                                                ServletConfig config, HttpSession session,
                                                HttpServletRequest request) {
        String hrefPrefix =
                "../resources/resource.jsp?region=region3&item=resource_browser_menu&path=";
        String hrefPostfix = (screenWidth != null) ? "&screenWidth=" + screenWidth : "";
        String patchPath = RegistryUtils.getParentPath(path);
        String minorPath = RegistryUtils.getParentPath(patchPath);
        String majorPath = RegistryUtils.getParentPath(minorPath);
        String servicePath = RegistryUtils.getParentPath(majorPath);
        String versions[] = version.split("[.]");
        StringBuffer sb = new StringBuffer("$1type=\"hidden\"$2");
        if (type.equals("collection")) {
            sb.append("<a href=\"").append(hrefPrefix).append(majorPath.replaceAll("&", "%26"))
                    .append(hrefPostfix).append(
                    "\">").append(versions[0]).append("</a>");
            sb.append(".");
            sb.append("<a href=\"").append(hrefPrefix).append(minorPath.replaceAll("&", "%26"))
                    .append(hrefPostfix).append(
                    "\">").append(versions[1]).append("</a>");
            sb.append(".");
            sb.append("<a href=\"").append(hrefPrefix).append(patchPath.replaceAll("&", "%26"))
                    .append(hrefPostfix).append(
                    "\">").append(versions[2]).append("</a>");
            sb.append(append);
        } else if (type.equals("patch")) {
            sb.append("<a href=\"").append(hrefPrefix).append(majorPath.replaceAll("&", "%26"))
                    .append(hrefPostfix).append(
                    "\">").append(versions[0]).append("</a>");
            sb.append(".");
            sb.append("<a href=\"").append(hrefPrefix).append(minorPath.replaceAll("&", "%26"))
                    .append(hrefPostfix).append(
                    "\">").append(versions[1]).append("</a>");
            sb.append(".");
            sb.append("<a href=\"javascript:void(0)\">").append(versions[2]).append("</a>");
            sb.append(append);
            try {
                int[] adjacentVersions =
                        getAdjacentVersions(config, session, minorPath,
                                Integer.parseInt(versions[2]));
                sb.append("&nbsp;");
                if (adjacentVersions[0] > -1) {
                    sb.append("<a class=\"icon-link\" style=\"background-image: ").append(
                            "url(../resources/images/arrow-up.gif);float:none !important;").append(
                            "margin-bottom:0px !important;margin-top:0px !important;").append(
                            "margin-left:0px !important\" href=\"").append(hrefPrefix)
                            .append(minorPath.replaceAll("&", "%26")).append(hrefPostfix)
                            .append("/").append(adjacentVersions[0]).append("\" title=\"").append(
                            CarbonUIUtil.geti18nString(
                                    "previous.version",
                                    "org.wso2.carbon.governance.generic.ui.i18n.Resources",
                                    request.getLocale())).append(": ").append(versions[0]).append(
                            ".").append(versions[1]).append(".").append(adjacentVersions[0]).append(
                            "\"/>");
                }
                if (adjacentVersions[1] > -1) {
                    sb.append("<a class=\"icon-link\" style=\"background-image: ").append(
                            "url(../resources/images/arrow-right.gif);float:none !important;")
                            .append("margin-bottom:0px !important;margin-top:0px !important;")
                            .append("margin-left:0px !important\" href=\"").append(hrefPrefix)
                            .append(minorPath.replaceAll("&", "%26")).append(hrefPostfix)
                            .append("/").append(adjacentVersions[1]).append("\" title=\"").append(
                            CarbonUIUtil.geti18nString(
                                    "next.version",
                                    "org.wso2.carbon.governance.generic.ui.i18n.Resources",
                                    request.getLocale())).append(": ").append(versions[0]).append(
                            ".").append(versions[1]).append(".").append(adjacentVersions[1]).append(
                            "\"/>");
                }
            } catch (Exception ignore) {
            }
        } else if (type.equals("minor")) {
            sb.append("<a href=\"").append(hrefPrefix).append(majorPath.replaceAll("&", "%26"))
                    .append(hrefPostfix).append(
                    "\">").append(versions[0]).append("</a>");
            sb.append(".");
            sb.append("<a href=\"javascript:void(0)\">").append(versions[1]).append("</a>");
            sb.append(".");
            sb.append("<a href=\"").append(hrefPrefix).append(patchPath.replaceAll("&", "%26"))
                    .append(hrefPostfix).append(
                    "\">").append(versions[2]).append("</a>");
            sb.append(append);
            try {
                int[] adjacentVersions =
                        getAdjacentVersions(config, session, majorPath,
                                Integer.parseInt(versions[1]));
                sb.append("&nbsp;");
                if (adjacentVersions[0] > -1) {
                    String latestPatch =
                            getGreatestChildVersion(config, session,
                                    majorPath + "/" + adjacentVersions[0]);
                    sb.append("<a class=\"icon-link\" style=\"background-image: ").append(
                            "url(../resources/images/arrow-up.gif);float:none !important;").append(
                            "margin-bottom:0px !important;margin-top:0px !important;").append(
                            "margin-left:0px !important\" href=\"").append(hrefPrefix)
                            .append(majorPath.replaceAll("&", "%26")).append(hrefPostfix)
                            .append("/").append(adjacentVersions[0]).append("\" title=\"").append(
                            CarbonUIUtil.geti18nString(
                                    "previous.version",
                                    "org.wso2.carbon.governance.generic.ui.i18n.Resources",
                                    request.getLocale())).append(": ").append(versions[0]).append(
                            ".").append(adjacentVersions[0]).append(".").append(latestPatch).append(
                            "\"/>");
                }
                if (adjacentVersions[1] > -1) {
                    String latestPatch =
                            getGreatestChildVersion(config, session,
                                    majorPath + "/" + adjacentVersions[1]);
                    sb.append("<a class=\"icon-link\" style=\"background-image: ").append(
                            "url(../resources/images/arrow-right.gif);float:none !important;")
                            .append("margin-bottom:0px !important;margin-top:0px !important;")
                            .append("margin-left:0px !important\" href=\"").append(hrefPrefix)
                            .append(majorPath.replaceAll("&", "%26")).append(hrefPostfix)
                            .append("/").append(adjacentVersions[1]).append("\" title=\"").append(
                            CarbonUIUtil.geti18nString(
                                    "next.version",
                                    "org.wso2.carbon.governance.generic.ui.i18n.Resources",
                                    request.getLocale())).append(": ").append(versions[0]).append(
                            ".").append(adjacentVersions[1]).append(".").append(latestPatch).append(
                            "\"/>");
                }
            } catch (Exception ignore) {
            }
        } else if (type.equals("major")) {
            sb.append("<a href=\"javascript:void(0)\">").append(versions[0]).append("</a>");
            sb.append(".");
            sb.append("<a href=\"").append(hrefPrefix).append(minorPath.replaceAll("&", "%26"))
                    .append(hrefPostfix).append(
                    "\">").append(versions[1]).append("</a>");
            sb.append(".");
            sb.append("<a href=\"").append(hrefPrefix).append(patchPath.replaceAll("&", "%26"))
                    .append(hrefPostfix).append(
                    "\">").append(versions[2]).append("</a>");
            sb.append(append);
            try {
                int[] adjacentVersions =
                        getAdjacentVersions(config, session, servicePath,
                                Integer.parseInt(versions[0]));
                sb.append("&nbsp;");
                if (adjacentVersions[0] > -1) {
                    String latestMinor =
                            getGreatestChildVersion(config, session,
                                    servicePath + "/" + adjacentVersions[0]);
                    String latestPatch =
                            getGreatestChildVersion(config, session,
                                    servicePath + "/" + adjacentVersions[0] + "/" + latestMinor);
                    sb.append("<a class=\"icon-link\" style=\"background-image: ").append(
                            "url(../resources/images/arrow-up.gif);float:none !important;").append(
                            "margin-bottom:0px !important;margin-top:0px !important;").append(
                            "margin-left:0px !important\" href=\"").append(hrefPrefix)
                            .append(servicePath.replaceAll("&", "%26")).append(hrefPostfix)
                            .append("/").append(adjacentVersions[0]).append("\" title=\"").append(
                            CarbonUIUtil.geti18nString(
                                    "previous.version",
                                    "org.wso2.carbon.governance.generic.ui.i18n.Resources",
                                    request.getLocale())).append(": ").append(adjacentVersions[0])
                            .append(".").append(latestMinor).append(".").append(latestPatch).append(
                            "\"/>");
                }
                if (adjacentVersions[1] > -1) {
                    String latestMinor =
                            getGreatestChildVersion(config, session,
                                    servicePath + "/" + adjacentVersions[1]);
                    String latestPatch =
                            getGreatestChildVersion(config, session,
                                    servicePath + "/" + adjacentVersions[1] + "/" + latestMinor);
                    sb.append("<a class=\"icon-link\" style=\"background-image: ").append(
                            "url(../resources/images/arrow-right.gif);float:none !important;")
                            .append("margin-bottom:0px !important;margin-top:0px !important;")
                            .append("margin-left:0px !important\" href=\"").append(hrefPrefix)
                            .append(servicePath.replaceAll("&", "%26")).append(hrefPostfix)
                            .append("/").append(adjacentVersions[1]).append("\" title=\"").append(
                            CarbonUIUtil.geti18nString(
                                    "next.version",
                                    "org.wso2.carbon.governance.generic.ui.i18n.Resources",
                                    request.getLocale())).append(": ").append(adjacentVersions[1])
                            .append(".").append(latestMinor).append(".").append(latestPatch).append(
                            "\"/>");
                }
            } catch (Exception ignore) {
            }
        }
        return basicVersionElement.replaceAll(
                "(<input[^>]*)type=\"text\"([^>]*id=\"id_Overview_Version\"[^>]*>)", sb.toString());
    }
}
