package com.github.shadowsocks.utils

import java.util

import android.graphics.{Bitmap, BitmapFactory}
import com.google.zxing.common.{GlobalHistogramBinarizer, HybridBinarizer}
import com.google.zxing.{BarcodeFormat, BinaryBitmap, DecodeHintType, MultiFormatReader, RGBLuminanceSource}

object QRCodeDecoder {
  val allFormats = new util.ArrayList[BarcodeFormat]()
  allFormats.add(BarcodeFormat.AZTEC)
  allFormats.add(BarcodeFormat.CODABAR)
  allFormats.add(BarcodeFormat.CODE_39)
  allFormats.add(BarcodeFormat.CODE_93)
  allFormats.add(BarcodeFormat.CODE_128)
  allFormats.add(BarcodeFormat.DATA_MATRIX)
  allFormats.add(BarcodeFormat.EAN_8)
  allFormats.add(BarcodeFormat.EAN_13)
  allFormats.add(BarcodeFormat.ITF)
  allFormats.add(BarcodeFormat.MAXICODE)
  allFormats.add(BarcodeFormat.PDF_417)
  allFormats.add(BarcodeFormat.QR_CODE)
  allFormats.add(BarcodeFormat.RSS_14)
  allFormats.add(BarcodeFormat.RSS_EXPANDED)
  allFormats.add(BarcodeFormat.UPC_A)
  allFormats.add(BarcodeFormat.UPC_E)
  allFormats.add(BarcodeFormat.UPC_EAN_EXTENSION)
  val HINTS= new util.EnumMap[DecodeHintType, Object](classOf[DecodeHintType])
  HINTS.put(DecodeHintType.TRY_HARDER, BarcodeFormat.QR_CODE)
  HINTS.put(DecodeHintType.POSSIBLE_FORMATS, allFormats)
  HINTS.put(DecodeHintType.CHARACTER_SET, "utf-8")

  def syncDecodeQRCodeFromPath(path: String): Option[String] = {
    getBitmapFromPath(path) match {
      case Some(bitmap) => syncDecodeQRCode(bitmap)
      case None => None
    }
  }

  def syncDecodeQRCode(bitmap: Bitmap): Option[String] = {
    var source: RGBLuminanceSource = null
    try {
      val width = bitmap.getWidth
      val height = bitmap.getHeight
      val pixels = new Array[Int](width * height)
      bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
      source = new RGBLuminanceSource(width, height, pixels)
      val result = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)), HINTS)
      return Some(result.getText)
    } catch {
      case e: Exception => {
          e.printStackTrace()
          if (source != null) {
            try {
              val result = new MultiFormatReader().decode(new BinaryBitmap(new GlobalHistogramBinarizer(source)), HINTS)
              return Some(result.getText)
            } catch {
              case e: Exception =>
            }
          }
      }
    }
    None
  }

  def getBitmapFromPath (path: String): Option[Bitmap] = {
      try {
        val options = new BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, options)
        var sampleSize = options.outHeight / 400
        if (sampleSize <= 0) {
          sampleSize = 1
        }
        options.inSampleSize = sampleSize
        options.inJustDecodeBounds = false
        val bitmap = BitmapFactory.decodeFile(path, options)
        Some(bitmap)
      } catch {
        case e: Exception => None
      }
  }
}

