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
  appSetting "clientId"
  appSetting "clientSecret"
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
  state.appAccessToken = createAccessToken()
  log.debug "generated app access token ${state.appAccessToken}"

  def oauthParams = [
  response_type: "code",
  client_id: getClientId(),
  redirect_uri: "https://graph.api.smartthings.com/api/token/${state.appAccessToken}/smartapps/installations/${app.id}/receiveCode"
  ]
  def redirectUrl =  getVendorAuthPath() + '?' + toQueryString(oauthParams)
  log.debug "tokened redirect_uri = ${oauthParams.redirect_uri}"

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

def getVendorDeviceStateInfo(device) {
  return [ "uuid": device.uuid, "token": device.token ]
}

def createDevices(smartDevices) {
  log.debug "called createDevices with ${smartDevices}"
  smartDevices.each { smartDevice ->
    log.debug "checking if ${smartDevice.id} needs to be created"

    def usesArguments = false
    def commandInfo = ""
    def commandArray = [ "_deviceInfo" ]

    smartDevice.supportedCommands.each { command ->
      commandInfo += "<b>${command.name}<b>( ${command.arguments.join(', ')} )<br/>"
      commandArray.push(command.name)
      usesArguments = usesArguments || command.arguments.size()>0
    }

    // def capabilitiesString = "<b>capabilities:<b><br/>" +
    // smartDevice.capabilities.each { capability
    //   capabilitiesString += "<b>${capability.name}</b><br/>"
    // }

    if (state.vendorDevices[smartDevice.id]) {
      log.debug "the device ${smartDevice.id} has already been created"
      return;
    }

    log.debug "creating device for ${smartDevice.id}"

    def messageSchema = [
      "type": "object",
      "title": "Command",
      "properties": [
        "command": [
          "type": "string",
          "enum": commandArray,
          "default": "_deviceInfo"
        ]
      ]
    ]

    // if (commandArray.size()>1) {
    //   messageSchema."properties"."delay" = [
    //     "type": "number",
    //     "title": "delay (ms)"
    //   ]
    // }

    if (usesArguments) {
      messageSchema."properties"."arguments" = [
        "type": "array",
        "description": commandInfo,
        "readOnly": !usesArguments,
        "items": [
          "type": "string",
          "title": "arg"
        ]
      ]
    }

    def deviceProperties = [
      "messageSchema": messageSchema,
      "needsSetup": false,
      "online": true,
      "name": "${smartDevice.displayName}",
      "smartDeviceId": "${smartDevice.id}",
      "logo": "https://www.smartthings.com/about/media/resources/SmartThings-Ringed-FullColor.png",
      "owner": "${state.myUUID}",
      "configureWhitelist": [],
      "discoverWhitelist": [ "${state.myUUID}" ],
      "type": "device:${smartDevice.name.replaceAll('\\s','-').toLowerCase()}",
      "category": "smart-things",
      "meshblu": [
        "messageHooks": [
          [
            "url": "https://graph.api.smartthings.com/api/token/${state.appAccessToken}/smartapps/installations/${app.id}/message",
            "method": "POST",
            "generateAndForwardMeshbluCredentials": false
          ]
        ]
      ]
    ]

    def postParams = [
    uri: apiUrl() + "devices",
    headers: ["Authorization": "Bearer ${state.vendorAccessToken}"],
    body: groovy.json.JsonOutput.toJson(deviceProperties) ]

    log.debug "calling httpPost with params ${postParams}"

    try {
      httpPostJson(postParams) { response ->
        log.debug "here is your dumb device: ${response.data}"
        state.vendorDevices[smartDevice.id] = getVendorDeviceStateInfo(response.data)
      }
    } catch (e) {
      log.debug "you suck ${e}"
    }
  }
}

def devicesPage() {
  def postParams = [
  uri: apiUrl() + "v2/whoami",
  headers: ["Authorization": "Bearer ${state.vendorAccessToken}"]]

  log.debug "fetching url ${postParams.uri}"
  try {
    httpGet(postParams) { response ->
      state.myUUID = response.data.uuid
      log.debug "my uuid ${state.myUUID}"
    }
  } catch (e) {
    log.debug "whoami error ${e}"
  }

  postParams.uri = apiUrl() + "mydevices"
  def numDevices

  state.vendorDevices = [:]

  log.debug "fetching url ${postParams.uri}"
  try {
    httpGet(postParams) { response ->
      log.debug "devices json ${response.data.devices}"
      numDevices = response.data.devices.size()
      response.data.devices.each { device ->
        if (device.smartDeviceId) {
          log.debug "found device ${device.uuid} with smartDeviceId ${device.smartDeviceId}"
          state.vendorDevices[device.smartDeviceId] = getVendorDeviceStateInfo(device)
        }
        log.debug "has device: ${device.uuid} ${device.name} ${device.type}"
      }
    }
  } catch (e) {
    log.debug "devices error ${e}"
  }

  selectedCapabilities.each { capability ->
    log.debug "checking devices for capability ${capability}"
    createDevices(settings["${capability}Capability"])
  }

  return dynamicPage(name: "devicesPage", title: "Devices", install: true) {
    section {
      paragraph title: "my uuid:", "${state.myUUID}"
      paragraph title: "number devices:", "${numDevices}"
      paragraph title: "your smart devices:", "${state.vendorDevices}"
    }
  }
}

def receiveCode() {
  // revokeAccessToken()
  // state.appAccessToken = createAccessToken()
  log.debug "generated app access token ${state.appAccessToken}"

  def postParams = [
  uri: getVendorTokenPath(),
  body: [
  client_id: getClientId(),
  client_secret: getClientSecret(),
  grant_type: "authorization_code",
  code: params.code ] ]

  def goodResponse = "<html><body><p>&nbsp;</p><h2>Received Octoblu Token!</h2><h3>Click 'Done' to finish setup.</h3></body></html>"
  def badResponse = "<html><body><p>&nbsp;</p><h2>Something went wrong...</h2><h3>PANIC!</h3></body></html>"
  log.debug "authorizeToken with postParams ${postParams}"

  try {
    httpPost(postParams) { response ->
      log.debug "response: ${response.data}"
      state.vendorAccessToken = response.data.access_token
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
