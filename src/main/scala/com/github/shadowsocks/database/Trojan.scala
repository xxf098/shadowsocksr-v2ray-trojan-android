package com.github.shadowsocks.database

import java.net.URI

import scala.util.Try


object Trojan {
  def apply: Trojan = {
    val cipherList =  "ECDHE-ECDSA-AES128-GCM-SHA256:" +
    "ECDHE-RSA-AES128-GCM-SHA256:" +
    "ECDHE-ECDSA-CHACHA20-POLY1305:" +
    "ECDHE-RSA-CHACHA20-POLY1305:" +
    "ECDHE-ECDSA-AES256-GCM-SHA384:" +
    "ECDHE-RSA-AES256-GCM-SHA384:" +
    "ECDHE-ECDSA-AES256-SHA:" +
    "ECDHE-ECDSA-AES128-SHA:" +
    "ECDHE-RSA-AES128-SHA:" +
    "ECDHE-RSA-AES256-SHA:" +
    "DHE-RSA-AES128-SHA:" +
    "DHE-RSA-AES256-SHA:" +
    "AES128-SHA:" +
    "AES256-SHA:" +
    "DES-CBC3-SHA"
    val tls13CipherList = "TLS_AES_128_GCM_SHA256:" +
    "TLS_CHACHA20_POLY1305_SHA256:" +
    "TLS_AES_256_GCM_SHA384"
    new Trojan(
      "127.0.0.1",
      1081,
      "",
      443,
      "",
      true,
      "",
      false,
      cipherList,
      tls13CipherList
    )
  }
}

case class Trojan(localAddr: String,
                  localPort: Int,
                  remoteAddr: String,
                  remotePort: Int,
                  password: String,
                  verifyCert: Boolean,
                  caCertPath: String,
                  enableIpv6: Boolean,
                  cipherList: String,
                  tls13CipherList: String
                 )

case class TrojanURL(remoteAddr: String,
                     remotePort: Int,
                     password: String,
                     remark: String
                    ) {
  def parseURL(url: String): Option[TrojanURL] =  {
    Try(new URI(url))
        .filter(uri => uri.getScheme == "trojan")
        .map(uri => TrojanURL(uri.getHost, uri.getPort, uri.getUserInfo, ""))
      .toOption
  }
}