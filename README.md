# WSO2 Governance Registry - Exemption to Life-cycle Policies for a particular period (RM Feature- #477)

This feature is developed to enable the exemption of users, from requiring certain privileges to perform certain life-cycle operations during a given period, based on some exemption policy.

***


## Design and Implementation:

G-reg contains artifacts which can be resources or collections, for which we can assign a specified life-cycle. Life-cycle configuration is a xml file called configuration.xml and we can define the lifecycle elements and attributes there.

Exemption Policy is achieved by introducing new element timeValidation to lifecycle configuration, and a child element timeValidity and the attributes of it. They are startDate and endDate. You can check it in the configurations.xml file which has uploded here.

Then the functionality is achieved from 3 java classes and a jsp file.

`DefaultLifeCycle.java` - org.wso2.carbon.governance.registry.extensions.aspects.DefaultLifeCycle.java
       
`TimeWindowBean.java` - org.wso2.carbon.governance.registry.extensions.beans.TimeWindowBean.java
       
`Utils.java` -
org.wso2.carbon.governance.registry.extensions.aspects.utils.Utils.java

`lifecycles_ajaxprocessor.jsp` - org.wso2.carbon.governance.custom.lifecycles.checklist.ui.lifecycles_ajaxprocessor.jsp
      
![flow](https://docs.google.com/drawings/d/1Rx54bYBa1Qrn5a_3ZkCJAeNXScFNXwbcNgYKXeJZGtE/pub?w=960&h=720)
* xml data is taken from `getTimeData` method implemented in `DefaultLifeCycle.java`.
* that data is put in to a Hash map `timeValidation` which consists of a state and it's time attributes.
* That Hash map is sent to a method called `checkTimeValidity` in which the logic is developed according to the time-policy. By comparing `currentTime` along with the policy, a notification is sent to the user and a boolean value `timeValidity` is generated.
* `TimeWindowBean` is the bean which holds the `isTimeValid` attribute which is again a boolean value.
* Generated boolean value from `checkTimeValidity` is set for `isTimeValid` attribute in TimeWindowBean.
* `getTimeValidity` mehtod in `Utils.java` and the `TimeWIndowBean` is sent to that.There the boolean value `isTimeValid` is maintained and it is sent to `lifecycles_ajaxprocessor.jsp`.
* According to the boolean value the UI elements will be enabled and disabled from the jsp.

## Setting up the feature:

* Download org.wso2.carbon.governance.registry.extensions and org.wso2.carbon.governance.custom.lifecycles.checklist.ui packages from the uploaded list.
* each of those have a jar file inside its /target/ location.
* Replace the jars inside <GREG_HOME>/repository/components/plugins/ from those.
  NOTE: you have to rename the newly copied one using a underscore for the hyphen it has.
* 
* Run the server.

## Functionality:

Scenario 1:
If you enter a date which is far ahead of currnet date, all things will act in normal way.UI element functionalities are there.
![scenario1](https://docs.google.com/drawings/d/1Ivdd9ClanU6mxhFRG1o6vyFwh579PkhkzoWYdtTYBPM/pub?w=960&h=720)

Scenario 2:
If you enter a day which is within the notofying period defined it will send a notification to the user.UI element functionalities are there.
![scenario2](https://docs.google.com/drawings/d/1q0W1LPKRJiH7_thDfQOElNGgZ5Q_0HMTD3Pa1YixebA/pub?w=960&h=720)

Scenario 3:
If you enter a day which is passed it will not only send a notification but also disable the UI elements so that you cannot take an action. You are exempted.
![scenario3](https://docs.google.com/drawings/d/1PEIaScawRtEcDU209AR2ZsbMBBltrKr7iMjo53BEuaA/pub?w=960&h=720)
