/**
 *  Copyright 2023 Pedro Andrade
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *	Wallbox Driver
 *
 * Author: Pedro Andrade
 *
 *	Updates:
 *	Date: 2023-04-17	v1.0     Initial release
 *  Date: 2023-04-22	v1.1     Change charger status API to retrieve additional attributes
 *                               Added ability to schedule periodic refresh with interval in minutes 
 *
 *  Note: Tested with a Wallbox Pulsar Plus only
 */

import groovy.transform.Field
import java.text.SimpleDateFormat

preferences {
        // credentials for wallbox portal
        input name: "userName", type: "string", title: "username", description: "Enter", required: true, defaultValue: "username"
        input name: "userPassword", type: "string", title: "password", description: "Enter", required: true, defaultValue: "password"

        // this driver manages a single charger device - insert the id of the device to be managed
        input name: "chargerId", type: "string", defaultValue: "12345", required: true, title: "Id of the charger"

        // number of days that a token remains valid, after this the system will automatically refresh prior to making a new API call
        input name: "tokenValidity", type: "number", defaultValue: 15, required: true, title: "Token validity in days"
    
        // log level
        input(name: "logLevel", type: "enum", title: "Log Level", options: ["0","1","2","3","4"], defaultValue: "2");

        // refresh interval
//        input(name: "refreshInterval", type:"number", title: "Refresh interval in minutes (0 for no schedule)", required: true, defaultValue: 5);
}


metadata {
	definition (name: "Wallbox Charger", namespace: "HE_pfta", author: "Pedro Andrade") {
	capability "EnergyMeter"    // includes attribute energy, used to measure a session's provided energy
    capability "PowerMeter"    // includes attribute power, used to measure instant power being provided by the charger
	capability "Polling"
	capability "Refresh"

	attribute "name", "string"
	attribute "status", "number"
	attribute "statusName", "string"    // mapped from status attribute
	attribute "locked", "number"
    attribute "charging_time", "string"
    attribute "max_charging_current", "number"
    attribute "energy_price","number"
    attribute "max_available_power","number"
    attribute "charging_speed","number"
    attribute "added_range","number"
    attribute "cost","number"
    attribute "current_mode","number"
    attribute "state_of_charge","number"
    attribute "bearerToken", "string"
    attribute "tokenTimestamp","string"

    command "setMaxChargingCurrent", [[name: "amperage", type: "NUMBER"]]
    command "lockUnlockCharger", [[name: "lock", type: "ENUM", constraints: [0,1]]]
    command "pauseResumeCharge", [[name: "action", type: "ENUM", constraints: ["PAUSE","RESUME"]]]
    command "restartCharger", [[name: "confirm", type: "STRING", description: "Write YES to confirm charger restart"]]
    command "resetToken"
    command "updateScheduledRefresh", [[name: "minutes", type: "NUMBER", description: "Interval in minutes (0 do disable)", required: true, defaultValue: 0]]
	}
}

@Field final Map statusNames = [
	0: "DISCONNECTED",
	14: "ERROR",
	15: "ERROR",
	161: "READY",
	162: "READY",
	163: "DISCONNECTED",
    164: "WAITING",
	165: "LOCKED",
	166: "UPDATING",
	177: "SCHEDULED",
	178: "PAUSED",
	179: "SCHEDULED",
	180: "WAITING",
	181: "WAITING",
	182: "PAUSED",
	183: "WAITING",
	184: "WAITING",
	185: "WAITING",
	186: "WAITING",
	187: "WAITING",
	188: "WAITING",
	189: "WAITING",
	193: "CHARGING",
	194: "CHARGING",
	195: "CHARGING",
	196: "DISCHARGING",
	209: "LOCKED",
	210: "LOCKED"
];

@Field final Map actions = [
	"PAUSE": 2,
	"RESUME": 1,
    "RESTART": 3
];

// ********************** Reused logging code from Ecowitt Wifi Gateway Hubitat Driver

Integer logGetLevel() {
  //
  // Get the log level as an Integer:
  //
  //   0) log only Errors
  //   1) log Errors and Warnings
  //   2) log Errors, Warnings and Info
  //   3) log Errors, Warnings, Info and Debug
  //   4) log Errors, Warnings, Info, Debug and Trace/diagnostic (everything)
  //
  // If the level is not yet set in the driver preferences, return a default of 2 (Info)
  // Declared public because it's being used by the child-devices as well
  //
  if (settings.logLevel != null) return (settings.logLevel.toInteger());
  return (2);
}

private void logError(String str) { log.error(str); }
private void logWarning(String str) { if (logGetLevel() > 0) log.warn(str); }
private void logInfo(String str) { if (logGetLevel() > 1) log.info(str); }
private void logDebug(String str) { if (logGetLevel() > 2) log.debug(str); }
private void logTrace(String str) { if (logGetLevel() > 3) log.trace(str); }
private void logData(Map data) {
  if (logGetLevel() > 3) {
    data.each {
      logTrace("$it.key = $it.value");
    }
  }
}


def poll() {
	logDebug("Executing 'poll'")
	refresh()
}

def refresh() { // retrieves latest values for device attributes
	
    logDebug("Executing 'refresh'")
    getCharger()
    
}

def refreshToken() { // checks if token must be refreshed and refreshes if needed - checks based on configured validity duration

    logDebug("refresh token")
    
    SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyyMMddHHmmss")
    tokenTS=timestampFormat.parse(device.currentValue("tokenTimestamp") as String)

    logTrace(tokenTS as String)
    
    if (tokenTS.plus(tokenValidity as int)<(new Date())) {
        logDebug("I need to renew the token")
        getToken()
	    pauseExecution(2000)
    }
}

def getToken() { // gets a new token using user credentials
    
    def request=[
        			uri: "https://api.wall-box.com",
        			path: "/auth/token/user",
        			requestContentType: "application/json",
                    headers: [Authorization: "Basic " + ("${userName}:${userPassword}").bytes.encodeBase64()]
                   	]

    logTrace("httpget: "+request)
    
    httpGet(request) { resp ->
                parseToken(resp)}
}

def getCharger(){ // retrieves last charger information from wallbox API
    
    refreshToken()
    
    def request=[
        			uri: "https://api.wall-box.com",
        			path: "/chargers/status/${chargerId}",
        			requestContentType: "application/json;charset=utf-8",
                    headers: [Authorization: "Bearer ${device.currentValue("bearerToken") as String}"]
                   	]
    

    logTrace("httpget: "+request)
    
    httpGet(request) { resp ->
                parseCharger(resp)}
}

def parseToken(resp) { // parses get token response and updates token attributes
    if(resp.status == 200) {
        sendEvent(name: "bearerToken", value: resp.data.jwt)
        SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyyMMddHHmmss")
        sendEvent(name: "tokenTimestamp", value: timestampFormat.format(new Date()))
        }
    else {logDebug("Error retrieving token")}
}

def parseCharger(resp) { // parses get charger response and updates attributes
    if(resp.status == 200) {
        resp.data.each {attr ->
            logTrace("Processing attribute: "+attr)
            switch (attr.getKey()) {
                case "config_data":
                    resp.data.config_data.each { confAttr ->
                        logTrace("Processing config attribute: "+confAttr)
                        switch (confAttr.getKey()) {
                            case "name":
                            case "locked":
                            case "max_charging_current":
                            case "energy_price":
                                sendEvent(name:confAttr.getKey(), value: confAttr.getValue())
                            break;
                            default:
                                logTrace("Not processed config attribute!")
                        }
                    }
                break;
                case "max_available_power":
                case "charging_speed":
                case "added_range":
                case "cost":
                case "current_mode":
                case "state_of_charge":
                    sendEvent(name:attr.getKey(), value: attr.getValue())
                break;
                case "status_id":
                    sendEvent(name:"status", value: attr.getValue())
                    sendEvent(name:"statusName", value: statusNames.getAt(attr.getValue()))
                break;
                case "added_energy":
                    sendEvent(name:"energy", value: attr.getValue())
                break;
                case "charging_power":
                    sendEvent(name:"power", value: attr.getValue())
                break;
                case "charging_time":
                    String timestamp = new GregorianCalendar( 0, 0, 0, 0, 0, attr.getValue(), 0 ).time.format( 'HH:mm:ss' )
                    sendEvent(name:"charging_time", value: timestamp)
                break;
                default:
                    logTrace("Not processed attribute!")
            } 
        }
    }
    else {logDebug("Error retrieving chargers")}
}


def setMaxChargingCurrent(amperage) { // allows to change the maximum current to be used for charging

    refreshToken()
    
    def request=[
        			uri: "https://api.wall-box.com",
                    path: "/v2/charger/${chargerId}",
         			requestContentType: "application/json;charset=utf-8",
                    headers: [Authorization: "Bearer ${device.currentValue("bearerToken") as String}"],
                    body: ["maxChargingCurrent": "${amperage}"]
                   	]

    logTrace("httpput: "+request)

    try {
        httpPutJson(request) { resp ->
            if (!resp.success) {
                logDebug("setMaxChargingCurrent FAILED")
            } else {
                logDebug("setMaxChargingCurrent SUCCESS")
                pauseExecution(5000) // wait for status update before refreshing
                refresh()
            }
        }
    } catch (Exception e) {
        logError(e as String)
        logDebug("setMaxChargingCurrent EXCEPTION")
    }
}


def lockUnlockCharger(lock) { // allows locking or unlocking charger

    refreshToken()
    
    def request=[
        			uri: "https://api.wall-box.com",
                    path: "/v2/charger/${chargerId}",
         			requestContentType: "application/json;charset=utf-8",
                    headers: [Authorization: "Bearer ${device.currentValue("bearerToken") as String}"],
                    body: ["locked": "${lock}"]
                   	]

    logTrace("httpput: "+request)

    try {
        httpPutJson(request) { resp ->
            if (!resp.success) {
                logDebug("lockUnlockCharger FAILED")
            } else {
                logDebug("lockUnlockCharger SUCCESS")
                pauseExecution(5000) // wait for status update before refreshing
                refresh()
            }
        }
    } catch (Exception e) {
        logError(e as String)
        logDebug("lockUnlockCharger EXCEPTION")
    }
}


def pauseResumeCharge(action) { // allows pausing and resuming charging

    refreshToken()
    
    def request=[
        			uri: "https://api.wall-box.com",
                    path: "/v3/chargers/${chargerId}/remote-action",
         			requestContentType: "application/json;charset=utf-8",
                    headers: [Authorization: "Bearer ${device.currentValue("bearerToken") as String}"],
                    body: ["action": "${actions.getAt(action)}"]
                   	]

    logTrace("httppost: "+request)

    try {
        httpPostJson(request) { resp ->
            if (!resp.success) {
                logDebug("pauseResumeCharge FAILED")
            } else {
                logDebug("pauseResumeCharge SUCCESS")
                pauseExecution(5000) // wait for status update before refreshing
                refresh()
            }
        }
    } catch (Exception e) {
        logError(e as String)
        logDebug("pauseResumeCharge EXCEPTION")
    }
}

def restartCharger(confirm) { // allows restarting charger - requires parameter = "YES" to prevent accidental restarting from the UI
    if (confirm=="YES") {
        logDebug("restarting charger")
        pauseResumeCharge("RESTART")
    }
}

def installed(){ // initializes token upon initial installation
    resetToken()
}

def resetToken() { // forces token reset
	sendEvent(name: "bearerToken", value: "null")
    sendEvent(name: "tokenTimestamp", value: "19000101000000")
} 

private updateScheduledRefresh(minutes) { // schedules refresh according to the parameter in minutes
    logDebug("updateScheduledRefresh()")
    unschedule()
    // Get minutes from settings
    //def minutes = settings.refreshInterval?.toInteger()
    if (!minutes || minutes==0) {
        logDebug("Will not enable scheduled refresh")
    } else {
        logDebug("Scheduling polling task for every '${minutes}' minutes")
        if (minutes == 1){
            runEvery1Minute(refresh)
        } else {
            "runEvery${minutes}Minutes"(refresh)
        }
    }
}
