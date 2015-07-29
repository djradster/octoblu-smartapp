/**

*/

import java.text.DecimalFormat

private apiUrl()              { "https://meshblu.octoblu.com/" }
private getVendorName()       { "Octoblu" }
private getVendorIcon()       { "https://ds78apnml6was.cloudfront.net/device/blu.svg" }
private getVendorAuthPath()   { "https://oauth.octoblu.com/authorize" }
private getVendorTokenPath()  { "https://oauth.octoblu.com/access_token" }
private getClientId()         { (appSettings.clientId ? appSettings.clientId : "d2336830-6d7b-4325-bf79-391c5d4c270e") }
private getClientSecret()     { (appSettings.clientSecret ? appSettings.clientSecret : "b4edb065c5ab3b09390e724be6523803dd80290a") }
private getServerUrl()        { appSettings.serverUrl }

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
  page(name: "authPage")
  page(name: "subscribePage")
  page(name: "devicesPage")
}

mappings {
  path("/receiveCode") {
    action: [
    POST: "receiveCode",
    GET: "receiveCode"
    ]
  }
}

def authPage() {
  if(!state.appAccessToken) {
    state.appAccessToken = createAccessToken()
    log.debug "generated app access token ${state.appAccessToken}"
  }
  def oauthParams = [
  response_type: "code",
  client_id: getClientId(),
  redirect_uri: "https://graph.api.smartthings.com/api/token/${state.appAccessToken}/smartapps/installations/${app.id}/receiveCode"
  ]
  def redirectUrl =  getVendorAuthPath() + '?' + toQueryString(oauthParams)

  def isRequired = !state.vendorAccessToken
  return dynamicPage(name: "authPage", title: "Octoblu Authentication", nextPage:(isRequired ? null : "subscribePage"), install: isRequired, uninstall: showUninstall) {
    section {
      log.debug "url: ${redirectUrl}"
      if (isRequired) {
        paragraph title: "Token does not exist.", "Please login to Octoblu to complete setup."
      } else {
        paragraph title: "Token created.", "Login is not required."
      }
      href url:redirectUrl, style:"embedded", title: "Login", required: isRequired, description:"Click to fetch Octoblu Token."
    }
    section {
      input name: "showUninstall", type: "bool", title: "uninstall", description: "false", submitOnChange: true
      if (showUninstall) {
        paragraph title: "so long and thanks for all the fish", "sorry to see me leave ;_;"
        paragraph title: "i really promise to try harder next time", "please ignore the big red button"
      }
    }
  }
}

def subscribePage() {
  return dynamicPage(name: "subscribePage", title: "Subscribe to Things", nextPage: "devicesPage") {
    section {
      input name: "selectedCapabilities", type: "enum", title: "capability filter",
      submitOnChange: true, multiple: true, required: false, options: [ "actuator", "sensor" ]
      for (capability in selectedCapabilities) {
        input name: "${capability}Capability".toString(), type: "capability.$capability", title: "$capability things", multiple: true, required: false
      }
    }
  }
}

def devicesPage() {
  def postParams = [
  uri: apiUrl() + "v2/whoami",
  headers: ["Authorization": "Bearer ${state.vendorAccessToken}"]]

  log.debug "fetching url ${postParams.uri}"
  httpGet(postParams) { response ->
    state.myUUID = response.data.uuid
    log.debug "my uuid ${state.myUUID}"
  }

  postParams.uri = apiUrl() + "mydevices"
  def numDevices

  log.debug "fetching url ${postParams.uri}"
  httpGet(postParams) { response ->
    log.debug "devices json ${response.data.devices}"
    numDevices = response.data.devices.size()
    response.data.devices.each { device ->
      log.debug "has device: ${device.uuid} ${device.name} ${device.type}"
    }
  }

  return dynamicPage(name: "devicesPage", title: "Devices", install: true) {
    section {
      paragraph "number devices: ${numDevices}"
    }
  }
}

def stringFromResponse(response) {
  def data = ""
  response.data.each { prop, val ->
    if (data != "") {
      data += "&"
    }
    data += prop
    if (val) {
      data += "=" + val
    }
  }
  return data
}

def receiveCode() {

  def postParams = [
  uri: getVendorTokenPath(),
  contentType: "application/x-www-form-urlencoded",
  body: [
  client_id: getClientId(),
  client_secret: getClientSecret(),
  grant_type: "authorization_code",
  code: params.code ] ]

  def goodResponse = "<html><body><p>&nbsp;</p><h2>Received Octoblu Token!</h2><h3>Click 'Done' to finish setup.</h3></body></html>"
  def badResponse = "<html><body><p>&nbsp;</p><h2>Something went wrong...</h2><h3>PANIC!</h3></body></html>"
  log.debug "posting to ${tokenUrl} with postParams ${postParams}"

  try {
    httpPost(postParams) { response ->
      state.vendorAccessToken = new groovy.json.JsonSlurper().parseText(stringFromResponse(response)).access_token
      log.debug "have octoblu tokens ${state.vendorAccessToken}"
      render contentType: 'text/html', data: (state.vendorAccessToken ? goodResponse : badResponse)
    }
  } catch(e) {
    log.debug "second leg oauth error ${e}"
    render contentType: 'text/html', data: badResponse
  }
}

String toQueryString(Map m) {
  return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
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
      settings."${capability}Capability".each { thing ->
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
