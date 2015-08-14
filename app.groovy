/**
The MIT License (MIT)

Copyright (c) 2015 Octoblu

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

import org.apache.commons.codec.binary.Base64
import java.text.DecimalFormat

private apiUrl()              { "https://meshblu.octoblu.com/" }
private getVendorName()       { "Octoblu" }
private getVendorIcon()       { "http://i.imgur.com/BjTfDYk.png" }
private getVendorAuthPath()   { "https://oauth.octoblu.com/authorize" }
private getVendorTokenPath()  { "https://oauth.octoblu.com/access_token" }
private getClientId()         { (appSettings.clientId ?: "d2336830-6d7b-4325-bf79-391c5d4c270e") }
private getClientSecret()     { (appSettings.clientSecret ?: "b4edb065c5ab3b09390e724be6523803dd80290a") }
private getServerUrl()        { appSettings.serverUrl }

definition(
name: "Octoblu",
namespace: "citrix",
author: "Octoblu",
description: "Connect SmartThings devices to Octoblu",
category: "SmartThings Labs",
iconUrl: "http://i.imgur.com/BjTfDYk.png",
iconX2Url: "http://i.imgur.com/BjTfDYk.png"
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
    GET: "receiveCode"
    ]
  }
  path("/message") {
    action: [
    POST: "receiveMessage"
    ]
  }
}

def authPage() {
  state.accessToken = createAccessToken()
  log.debug "generated app access token ${state.accessToken}"

  def oauthParams = [
  response_type: "code",
  client_id: getClientId(),
  redirect_uri: "https://graph.api.smartthings.com/api/token/${state.accessToken}/smartapps/installations/${app.id}/receiveCode"
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

def resetVendorDeviceToken(smartDeviceId) {
  def deviceUUID = state.vendorDevices[smartDeviceId].uuid
  if (!deviceUUID) {
    log.debug "no device uuid in resetVendorDeviceToken?"
    return
  }
  log.debug "getting new token for ${smartDeviceId}/${deviceUUID}"
  def postParams = [
  uri: apiUrl() + "devices/${deviceUUID}/token",
  headers: ["Authorization": "Bearer ${state.vendorAccessToken}"]]
  try {
    httpPost(postParams) { response ->
      state.vendorDevices[smartDeviceId] = getVendorDeviceStateInfo(response.data)
      log.debug "got new token for ${smartDeviceId}/${deviceUUID}"
    }
  } catch (e) {
    log.debug "unable to get new token ${e}"
  }
}

def createDevices(smartDevices) {
  smartDevices.each { smartDevice ->

    def usesArguments = false
    def commandInfo = ""
    def commandArray = [ ".value", ".state", ".device", ".events"]

    smartDevice.supportedCommands.each { command ->
      commandInfo += "<b>${command.name}<b>( ${command.arguments.join(', ')} )<br/>"
      commandArray.push(command.name)
      usesArguments = usesArguments || command.arguments.size()>0
    }

    // def capabilitiesString = "<b>capabilities:<b><br/>" +
    // smartDevice.capabilities.each { capability
    //   capabilitiesString += "<b>${capability.name}</b><br/>"
    // }

    log.debug "creating device for ${smartDevice.id}"

    def messageSchema = [
      "type": "object",
      "title": "Command",
      "properties": [
        "smartDeviceId" : [
          "type": "string",
          "readOnly": true,
          "default": "${smartDevice.id}"
        ],
        "command": [
          "type": "string",
          "enum": commandArray,
          "default": ".value"
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
      "logo": "https://i.imgur.com/TsXefbK.png",
      "owner": "${state.myUUID}",
      "configureWhitelist": [],
      "discoverWhitelist": ["${state.myUUID}"],
      "receiveWhitelist": ["*"],
      "sendWhitelist": ["*"],
      "type": "device:${smartDevice.name.replaceAll('\\s','-').toLowerCase()}",
      "category": "smart-things",
      "meshblu": [
        "messageHooks": [
          [
            "url": "https://graph.api.smartthings.com/api/token/${state.accessToken}/smartapps/installations/${app.id}/message",
            "method": "POST",
            "generateAndForwardMeshbluCredentials": false
          ]
        ]
      ]
    ]

    def params = [
    uri: apiUrl() + "devices",
    headers: ["Authorization": "Bearer ${state.vendorAccessToken}"],
    body: groovy.json.JsonOutput.toJson(deviceProperties) ]

    try {

      if (!state.vendorDevices[smartDevice.id]) {
        log.debug "creating new device for ${smartDevice.id}"
        httpPostJson(params) { response ->
          state.vendorDevices[smartDevice.id] = getVendorDeviceStateInfo(response.data)
        }
        return
      }

      params.uri = params.uri + "/${state.vendorDevices[smartDevice.id].uuid}"
      log.debug "the device ${smartDevice.id} has already been created, updating ${params.uri}"
      httpPutJson(params) { response ->
        resetVendorDeviceToken(smartDevice.id);
      }

    } catch (e) {
      log.debug "unable to create new device ${e}"
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
    return
  }

  postParams.uri = apiUrl() + "devices?owner=${state.myUUID}&category=smart-things"

  state.vendorDevices = [:]

  log.debug "fetching url ${postParams.uri}"
  try {
    httpGet(postParams) { response ->
      log.debug "devices json ${response.data.devices}"
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

  def devInfo = state.vendorDevices.collect { k, v -> "${k}: ${v.uuid}/ ${v.token} " }.sort().join("\n")

  return dynamicPage(name: "devicesPage", title: "Devices", install: true) {
    section {
      paragraph title: "my uuid:", "${state.myUUID}"
      paragraph title: "number of smart devices:", "${state.vendorDevices.size()}"
      paragraph title: "your smart devices:", "${devInfo}"
    }
  }
}

def receiveCode() {
  // revokeAccessToken()
  // state.accessToken = createAccessToken()
  log.debug "generated app access token ${state.accessToken}"

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

def getEventData(evt) {
  return [
  "id" : evt.id,
  "name" : evt.name,
  "value" : evt.value,
  "deviceId" : evt.deviceId,
  "hubId" : evt.hubId,
  "locationId" : evt.locationId,
  "installedSmartAppId" : evt.installedSmartAppId,
  "date" : evt.date,
  "dateValue": evt.dateValue,
  "isoDate" : evt.isoDate,
  "isDigital" : evt.isDigital(),
  "isPhysical" : evt.isPhysical(),
  "isStateChange" : evt.isStateChange(),
  "linkText" : evt.linkText,
  "description" : evt.description,
  "descriptionText" : evt.descriptionText,
  "displayName" : evt.displayName,
  "source" : evt.source,
  "unit" : evt.unit,
  "category" : "event",
  "type" : "device:smart-thing"
  ]
}

def eventForward(evt) {
  def eventData = [ "devices" : "*", "payload" : getEventData(evt) ]

  log.debug "sending event: ${groovy.json.JsonOutput.toJson(eventData)}"

  def vendorDevice = state.vendorDevices[evt.deviceId]
  if (!vendorDevice) {
    log.debug "aborting, vendor device for ${evt.deviceId} doesn't exist?"
    return
  }

  log.debug "using device ${vendorDevice}"

  def postParams = [
  uri: apiUrl() + "messages",
  headers: ["meshblu_auth_uuid": vendorDevice.uuid, "meshblu_auth_token": vendorDevice.token],
  body: groovy.json.JsonOutput.toJson(eventData) ]

  try {
    httpPostJson(postParams) { response ->
      log.debug "sent off device event"
    }
  } catch (e) {
    log.debug "unable to send device event ${e}"
  }
}

def receiveMessage() {
  log.debug("received data ${request.JSON}")
  def foundDevice = false
  settings.selectedCapabilities.each{ capability ->
    settings."${capability}Capability".each { thing ->
      if (!foundDevice && thing.id == request.JSON.payload.smartDeviceId) {
        foundDevice = true
        if (!request.JSON.payload.command.startsWith(".")) {
          def args = (request.JSON.payload.arguments ?: [])
          thing."${request.JSON.payload.command}"(*args)
        } else {
          log.debug "calling internal command ${request.JSON.payload.command}"
          def commandData = [:]
          switch (request.JSON.payload.command) {
            case ".value":
              log.debug "got command .value"
              thing.supportedAttributes.each { attribute ->
                commandData[attribute.name] = thing.latestValue(attribute.name)
              }
              break
            case ".state":
              log.debug "got command .state"
              thing.supportedAttributes.each { attribute ->
                commandData[attribute.name] = thing.latestState(attribute.name)?.value
              }
              break
            case ".device":
              log.debug "got command .device"
              commandData = [
                "id" : thing.id,
                "displayName" : thing.displayName,
                "name" : thing.name,
                "label" : thing.label,
                "capabilities" : thing.capabilities.collect{ thingCapability -> return thingCapability.name },
                "supportedAttributes" : thing.supportedAttributes.collect{ attribute -> return attribute.name },
                "supportedCommands" : thing.supportedCommands.collect{ command -> return ["name" : command.name, "arguments" : command.arguments ] }
              ]
              break
            case ".events":
              log.debug "got command .events"
              commandData.events = []
              thing.events().each { event ->
                commandData.events.push(getEventData(event))
              }
              break
            default:
              commandData.error = "unknown command"
              log.debug "unknown command ${request.JSON.payload.command}"
          }
          commandData.command = request.JSON.payload.command

          log.debug "done switch!"

          def vendorDevice = state.vendorDevices[thing.id]
          log.debug "with vendorDevice ${vendorDevice} for ${groovy.json.JsonOutput.toJson(commandData)}"

          def postParams = [
            uri: apiUrl() + "messages",
            headers: ["meshblu_auth_uuid": vendorDevice.uuid, "meshblu_auth_token": vendorDevice.token],
            body: groovy.json.JsonOutput.toJson([ "devices" : "*", "payload" : commandData ]) ]

          log.debug "posting params ${postParams}"

          try {
            log.debug "calling httpPostJson!"
            httpPostJson(postParams) { response ->
              log.debug "sent off command result"
            }
          } catch (e) {
            log.debug "unable to send command result ${e}"
          }

        }

        log.debug "done else"
      }
    }
  }
}

def updated() {
  unsubscribe()
  log.debug "Updated with settings: ${settings}"
  def subscribed = [:]
  settings.selectedCapabilities.each{ capability ->
    settings."${capability}Capability".each { thing ->
      if (subscribed[thing.id]) {
        return
      }
      subscribed[thing.id] = true
      thing.supportedAttributes.each { attribute ->
        log.debug "subscribe to attribute ${attribute.name}"
        subscribe thing, attribute.name, eventForward
      }
      thing.supportedCommands.each { command ->
        log.debug "subscribe to command ${command.name}"
        subscribeToCommand thing, command.name, eventForward
      }
      log.debug "subscribed to thing ${thing.id}"
    }
  }

  // def myToken = new String((new Base64()).decode(state.vendorAccessToken)).split(":")[1]
  //
  // log.debug "decoded accessToken: ${decodedData}"
  //
  // def params = [
  // uri: apiUrl() + "devices/${state.myUUID}/tokens/${myToken}",
  // headers: ["Authorization": "Bearer ${state.vendorAccessToken}"]]
  //
  // log.debug "fetching url ${params.uri}"
  // try {
  //   httpDelete(params) { response ->
  //     log.debug "revoked token for ${state.myUUID}...?"
  //     state.vendorAccessToken = null
  //   }
  // } catch (e) {
  //   log.debug "token delete error ${e}"
  //   return
  // }

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
