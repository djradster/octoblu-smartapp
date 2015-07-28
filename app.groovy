/**

*/

import java.text.DecimalFormat

private apiUrl()          { "https://meshblu.octoblu.com/" }
private getVendorName()   { "Octoblu" }
private getVendorIcon()   { "https://ds78apnml6was.cloudfront.net/device/blu.svg" }
private getClientId()     { appSettings.clientId }
private getClientSecret() { appSettings.clientSecret }
private getServerUrl()    { appSettings.serverUrl }

definition(
name: "Octoblu",
namespace: "citrix",
author: "Octoblu",
description: "Connect Octoblu to SmartThings.",
category: "SmartThings Labs",
iconUrl: "https://ds78apnml6was.cloudfront.net/device/blu.svg",
iconX2Url: "https://ds78apnml6was.cloudfront.net/device/blu.svg"
) {
  appSetting "uuid"
  appSetting "token"
  appSetting "serverUrl"
}

preferences {
  page(name: "Credentials", title: "Authentication", content: "authPage", nextPage: "welcomePage", install: false)
  page(name: "welcomePage", nextPage: "actionPage")
  page(name: "actionPage")
}

mappings {
  path("/receiveToken") {
    action: [
    POST: "receiveToken",
    GET: "receiveToken"
    ]
  }
}

def authPage() {
  // if(!state.accessToken) {
  //   state.accessToken = createAccessToken()
  // }
  def oauthParams = [
  response_type: "code",
  client_id: "d2336830-6d7b-4325-bf79-391c5d4c270e",
  redirect_uri: "https://graph.api.smartthings.com/api/smartapps/installations/${app.id}/receiveToken"
  ]
  def redirectUrl = "https://oauth.octoblu.com/authorize?"+ toQueryString(oauthParams)

  return dynamicPage(name: "Credentials", title: "Octoblu Authentication", nextPage:"welcomePage", uninstall: uninstallOption, install:false) {
    section {
      log.debug "url: ${redirectUrl}"
      def isRequired = !state.octobluBearerToken
      if (isRequired) {
        paragraph title: "Credentials do not exist!", "Please login to Octoblu to complete setup."
      }
      href url:redirectUrl, style:"embedded", required: isRequired, description:"Click to enter Octoblu Credentials."
    }
  }
}

def welcomePage() {
  dynamicPage(name: "welcomePage", title: "WELCOME") {
    section {
      input name: "selectedAction", type: "enum", options: ['add things', 'remove things', 'uninstall'], title: "configuration action", description: "", required: true
    }
  }
}

def actionPage() {

  dynamicPage(name: "actionPage", title: selectedAction.toUpperCase(), install: true, uninstall: selectedAction == 'uninstall') {
    if (selectedAction == 'add things') {
      section {
        input name: "selectedCapabilities", type: "enum", title: "capabilities filter",
        submitOnChange: true, multiple: true, required: false, options:
        [ "accelerationSensor",
        "actuator",
        "alarm",
        "battery",
        "beacon",
        "button",
        "carbonMonoxideDetector",
        "colorControl",
        "configuration",
        "contactSensor",
        "doorControl",
        "energyMeter",
        "illuminanceMeasurement",
        "imageCapture",
        "lock",
        "mediaController",
        "momentary",
        "motionSensor",
        "musicPlayer",
        "notification",
        "polling",
        "powerMeter",
        "presenceSensor",
        "refresh",
        "relativeHumidityMeasurement",
        "relaySwitch",
        "sensor",
        "signalStrength",
        "sleepSensor",
        "smokeDetector",
        "speechSynthesis",
        "stepSensor",
        "switch",
        "switchLevel",
        "temperatureMeasurement",
        "thermostat",
        "thermostatCoolingSetpoint",
        "thermostatFanMode",
        "thermostatHeatingSetpoint",
        "thermostatMode",
        "thermostatOperatingState",
        "thermostatSetpoint",
        "threeAxis",
        "tone",
        "touchSensor",
        "valve",
        "waterSensor" ]
      }
      section {
        for (capability in selectedCapabilities) {
          input name: "capability.${capability}", type: "capability.$capability", title: "$capability things", multiple: true, required: false
        }
      }
    }
    if (selectedAction == 'uninstall') {
      section {
        paragraph title: "so long and thanks for all the fish", "sorry to see me leave ;_;"
        paragraph title: "i really promise to try harder next time", "please ignore the big red button"
      }
    }
  }
}

def receiveToken() {
  state.octobluBearerToken = params.code
  log.debug "new bearer token: ${state.octobluBearerToken}"
  render contentType: 'text/html', data: "<html><body><p/><h2>Received Octoblu Token!</h2><h3>Click 'Done' to finish setup.</h3></body></html>"
}

def switchesHandler(evt) {
  log.debug "one of the configured switches changed states"
}

/*
mappings {
path("/message")	{ action:[ POST: "messageEventHandler"] }
}
*/

String toQueryString(Map m) {
  return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}

def buildCallbackUrl(suffix)
{
  log.debug "In buildRedirectUrl"

  def serverUrl = getServerUrl()
  return serverUrl + "/api/token/${state.accessToken}/smartapps/installations/${app.id}" + suffix
}

def apiGet(String path, Closure callback)
{
  httpGet([
  uri : apiUrl(),
  path : path,
  headers : [ 'Authorization' : 'Bearer ' + state.vendorAccessToken ]
  ]) { response ->
    callback.call(response)
  }
}

def apiPut(String path, cmd, Closure callback)
{
  httpPutJson([
  uri : apiUrl(),
  path: path,
  body: cmd,
  headers : [ 'Authorization' : 'Bearer ' + state.vendorAccessToken ]
  ]) { response ->
    callback.call(response)
  }
}

def initialize()
{
  log.debug "Initialized with settings: ${settings}"
}

def uninstalled()
{
  log.debug "In uninstalled"
}

def installed() {
  log.debug "Installed with settings: ${settings}"
}

def logger(evt) {
  def data = [ "name" : evt.name,
  "value" : evt.value,
  "description" : evt.description,
  "descriptionText" : evt.descriptionText,
  "source" : evt.source,
  "unit" : evt.unit,
  "deviceId" : evt.deviceId,
  "displayName" : evt.displayName,
  "hubId" : evt.hubId,
  "date" : ( evt.dateValue ? evt.dateValue : evt.date ),
  "locationId" : evt.locationId ]
  log.debug "event: ${data}"
}

def updated() {
  unsubscribe()
  log.debug "Updated with settings: ${settings}"
  if (settings.selectedAction == 'add things') {
    settings.selectedCapabilities.each{ capability ->
      settings."capability.${capability}".each { thing ->
        thing.supportedAttributes.each { attribute ->
          log.debug "subscribe to ${attribute.name}"
          subscribe thing, attribute.name, logger
        }
        thing.supportedCommands.each { command ->
          log.debug "subscribe to command ${command.name}"
          subscribeToCommand thing, command.name, logger
        }
        log.debug "subscribed to thing ${thing.id}"
      }
    }
  }
}

def debugEvent(message, displayEvent) {

  def results = [
  name: "appdebug",
  descriptionText: message,
  displayed: displayEvent
  ]
  log.debug "Generating AppDebug Event: ${results}"
  sendEvent (results)

}

private Boolean canInstallLabs()
{
  return hasAllHubsOver("000.011.00603")
}

private Boolean hasAllHubsOver(String desiredFirmware)
{
  return realHubFirmwareVersions.every { fw -> fw >= desiredFirmware }
}

private List getRealHubFirmwareVersions()
{
  return location.hubs*.firmwareVersionString.findAll { it }
}
