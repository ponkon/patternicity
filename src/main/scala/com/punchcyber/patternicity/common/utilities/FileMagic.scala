package com.punchcyber.patternicity.common.utilities

import java.io.{BufferedInputStream, InputStream, UnsupportedEncodingException}
import java.util

import com.punchcyber.patternicity.enums.filetypes.SupportedFileType
import org.apache.commons.compress.archivers.{ArchiveEntry, ArchiveInputStream, ArchiveStreamFactory}
import org.apache.commons.compress.compressors.{CompressorInputStream, CompressorStreamFactory}

import scala.collection.mutable
import scala.util.control.Breaks.{break, breakable}

object FileMagic {
    private def toBytes(offset: Int,bytes: Int*): Array[Byte] = {
        if(offset.equals(0)) {
            bytes.map(_.toByte).toArray
        }
        else {
            val pad: Array[Byte] = new Array[Byte](offset)
            val magic: Array[Byte] = bytes.map(_.toByte).toArray
            pad ++ magic
        }
    }
    
    def comp(magic: Array[Byte],file: Array[Byte]): Boolean = {
        var offset: Int = 0
        while(magic(offset).toInt.equals(0)) offset += 1
        
        util.Arrays.copyOfRange(magic,offset,magic.length).sameElements(util.Arrays.copyOfRange(file,offset,magic.length))
    }
    
    def findFileType(is: InputStream): Option[SupportedFileType] = {
        val bis: BufferedInputStream = new BufferedInputStream(is)
        
        val fileBytes: Array[Byte] = new Array[Byte](512)
        bis.mark(1024)
        bis.read(fileBytes)
        bis.reset()
        
        var fileType: SupportedFileType = SupportedFileType.UNSUPPORTED
        
        breakable {
            for((magicBytes,name) <- magics) {
                if(comp(magicBytes,fileBytes)) {
                    fileType = name
                    break()
                }
            }
        }
        
        Some(fileType)
    }
    
    @throws[UnsupportedEncodingException] // TODO: do a better exception...this is close enough for the next 30 seconds...30, 29, 28, 27 ...
    def recursiveFindFileType(name: SupportedFileType, is:InputStream): Option[Boolean] = {
        if(Array(SupportedFileType.EVTX,SupportedFileType.PCAP,SupportedFileType.BRO).contains(name)) {
            val bis: BufferedInputStream = new BufferedInputStream(is)
            
            val fileBytes: Array[Byte] = new Array[Byte](512)
            bis.mark(1024)
            bis.read(fileBytes)
            bis.reset()
            
            var fileType: Option[SupportedFileType] = None
            
            breakable {
                for((magicBytes,name) <- magics) {
                    if(comp(magicBytes,fileBytes)) {
                        fileType = Some(name)
                        break()
                    }
                }
            }
            
            fileType match {
                case Some(ft) =>
                    ft match {
                        case n if n.equals(name) =>
                            return Some(true)
                        
                        case compression if Array(SupportedFileType.GZIP,SupportedFileType.BZIP2).contains(compression) =>
                            val cis: CompressorInputStream = new CompressorStreamFactory().createCompressorInputStream(bis)
                            val r: Option[Boolean] = recursiveFindFileType(name,cis)
                            r match {
                                case Some(_) => return Some(true)
                                case None =>
                            }
                        
                        case archive if Array(SupportedFileType.TAR,SupportedFileType.ZIP).contains(archive) =>
                            val ais: ArchiveInputStream =  new ArchiveStreamFactory().createArchiveInputStream(bis)
                            
                            var entry: ArchiveEntry = ais.getNextEntry
                            while(entry != null) {
                                val r: Option[Boolean] = recursiveFindFileType(name,ais)
                                r match {
                                    case Some(_) => return Some(true)
                                    case None =>
                                }
                                
                                try {
                                    entry = ais.getNextEntry
                                } catch {
                                    // TODO: We may be overly broad here, the expected exception would be IOException, but we are saying that we want to stop iterating on ANY throwable error...not sure if this is the right approach
                                    case _: Throwable =>
                                        entry = null
                                }
                            }
                        case _ =>
                    }
                case None =>
            }
        }
        None
    }
    
    
    
    val magics: mutable.HashMap[Array[Byte],SupportedFileType]= mutable.HashMap[Array[Byte],SupportedFileType](
        toBytes(0  ,0x1f,0x8b,0x08)                                         -> SupportedFileType.GZIP,
        toBytes(0  ,0x42,0x5A,0x68)                                         -> SupportedFileType.BZIP2,
        toBytes(0  ,0x50,0x4b,0x03,0x04)                                    -> SupportedFileType.ZIP,
        toBytes(257,0x75,0x73,0x74,0x61,0x72)                               -> SupportedFileType.TAR,
        toBytes(0  ,0x45,0x6C,0x66,0x46,0x69,0x6C,0x65,0x00)                -> SupportedFileType.EVTX,
        toBytes(0  ,0xd4,0xc3,0xb2,0xa1)                                    -> SupportedFileType.PCAP,
        toBytes(0  ,0xd4,0xcd,0xb2,0xa1)                                    -> SupportedFileType.PCAP,
        toBytes(0  ,0xa1,0xb2,0xc3,0xd4)                                    -> SupportedFileType.PCAP,
        toBytes(0  ,0xa1,0xb2,0xcd,0xd4)                                    -> SupportedFileType.PCAP,
        toBytes(0  ,0x23,0x73,0x65,0x70,0x61,0x72,0x61,0x74,0x6f,0x72,0x20) -> SupportedFileType.BRO
    )
    
    
}
