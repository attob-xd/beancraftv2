package net.lax1dude.eaglercraft.internal.teavm;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Uint8Array;

import com.jcraft.jzlib.CRC32;
import com.jcraft.jzlib.GZIPInputStream;
import com.jcraft.jzlib.InflaterInputStream;

import net.lax1dude.eaglercraft.internal.PlatformAssets;

/**
 * Copyright (c) 2022 lax1dude. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */
public class EPKLoader {

	public static final void loadEPK(ArrayBuffer epkFile, Map<String, byte[]> loadedFiles) throws IOException {
		loadEPK(epkFile, "", loadedFiles);
	}

	public static final void loadEPK(ArrayBuffer epkFile, String path, Map<String, byte[]> loadedFiles) throws IOException {
		int byteLength = epkFile.getByteLength();
		int l = byteLength - 16;
		if(l < 1) {
			throw new IOException("EPK file is incomplete");
		}
		
		ArrayBufferInputStream is = new ArrayBufferInputStream(epkFile, 0, byteLength - 8);
		
		byte[] header = new byte[8];
		is.read(header);
		String type = readASCII(header);
		
		if(!"EAGPKG$$".equals(type)) {
			throw new IOException("Invalid EPK file type '" + type + "'");
		}
		
		Uint8Array readEndCode = Uint8Array.create(epkFile, byteLength - 8, 8);
		
		byte[] endCode = new byte[] { (byte)':', (byte)':', (byte)':', (byte)'Y',
				(byte)'E', (byte)'E', (byte)':', (byte)'>' };
		for(int i = 0; i < 8; ++i) {
			if(readEndCode.get(i) != endCode[i]) {
				throw new IOException("EPK file is missing EOF code (:::YEE:>)");
			}
		}
		
		String vers = readASCII(is);
		if(!vers.startsWith("ver2.")) {
			throw new IOException("Unknown or invalid EPK version: " + vers);
		}
		
		is.skip(is.read()); // skip filename
		is.skip(loadShort(is)); // skip comment
		is.skip(8); // skip millis date
		
		int numFiles = loadInt(is);
		
		char compressionType = (char)is.read();
		
		InputStream zis;
		switch(compressionType) {
		case 'G':
			zis = new GZIPInputStream(is);
			break;
		case 'Z':
			zis = new InflaterInputStream(is);
			break;
		case '0':
			zis = is;
			break;
		default:
			throw new IOException("Invalid or unsupported EPK compression: " + compressionType);
		}

		int blockFile = ('F' << 24) | ('I' << 16) | ('L' << 8) | 'E';
		int blockEnd = ('E' << 24) | ('N' << 16) | ('D' << 8) | '$';
		int blockHead = ('H' << 24) | ('E' << 16) | ('A' << 8) | 'D';
		
		if(path.length() > 0 && !path.endsWith("/")) {
			path = path + "/";
		}
		
		CRC32 crc32 = new CRC32();
		int blockType;
		for(int i = 0; i < numFiles; ++i) {
			
			blockType = loadInt(zis);
			
			if(blockType == blockEnd) {
				throw new IOException("Unexpected END when there are still " + (numFiles - i) + " files remaining");
			}
			
			String name = readASCII(zis);
			int len = loadInt(zis);
			
			if(i == 0) {
				if(blockType == blockHead) {
					byte[] readType = new byte[len];
					zis.read(readType);
					if(!"file-type".equals(name) || !"epk/resources".equals(readASCII(readType))) {
						throw new IOException("EPK is not of file-type 'epk/resources'!");
					}
					if(zis.read() != '>') {
						throw new IOException("Object '" + name + "' is incomplete");
					}
					continue;
				}else {
					throw new IOException("File '" + name + "' did not have a file-type block as the first entry in the file");
				}
			}
			
			if(blockType == blockFile) {
				if(len < 5) {
					throw new IOException("File '" + name + "' is incomplete");
				}
				
				int expectedCRC = loadInt(zis);
				
				byte[] load = new byte[len - 5];
				zis.read(load);

				if(len > 5) {
					crc32.reset();
					crc32.update(load, 0, load.length);
					if(expectedCRC != (int)crc32.getValue()) {
						throw new IOException("File '" + name + "' has an invalid checksum");
					}
				}
				
				if(zis.read() != ':') {
					throw new IOException("File '" + name + "' is incomplete");
				}
				String s = path + name;
				if(isServerAsset(s)) {
					PlatformAssets.serverAssets.put(s, load);
				}
				loadedFiles.put(s, load);
			}else {
				zis.skip(len);
			}

			if(zis.read() != '>') {
				throw new IOException("Object '" + name + "' is incomplete");
			}
		}
		
		if(loadInt(zis) != blockEnd) {
			throw new IOException("EPK missing END$ object");
		}
		
		zis.close();
	}
	
	private static final int loadShort(InputStream is) throws IOException {
		return (is.read() << 8) | is.read();
	}
	
	private static final int loadInt(InputStream is) throws IOException {
		return (is.read() << 24) | (is.read() << 16) | (is.read() << 8) | is.read();
	}
	
	private static final String readASCII(byte[] bytesIn) throws IOException {
		char[] charIn = new char[bytesIn.length];
		for(int i = 0; i < bytesIn.length; ++i) {
			charIn[i] = (char)((int)bytesIn[i] & 0xFF);
		}
		return new String(charIn);
	}
	
	private static final String readASCII(InputStream bytesIn) throws IOException {
		int len = bytesIn.read();
		char[] charIn = new char[len];
		for(int i = 0; i < len; ++i) {
			charIn[i] = (char)(bytesIn.read() & 0xFF);
		}
		return new String(charIn);
	}


	/**
	 * Picks the files the integrated server worker is sent, which is everything it can read:
	 * its asset map is replaced wholesale by this selection (see IPCPacketMapAssets), so a file
	 * left out here does not exist as far as the server is concerned.
	 *
	 * <p>The old test was {@code advancements || loot_tables || recipes || structures}, which
	 * was the complete list for 1.12.2 - that server read nothing else out of the jar. 1.18.2
	 * moved almost all of its behaviour into the built-in datapack under {@code data/}:
	 * dimension types, noise settings, biomes, carvers, features, processor lists, and every
	 * tag, on top of the four categories above, which now live there as well. A server without
	 * them cannot even build a world stem.
	 *
	 * <p>{@code assets/minecraft/lang/} is here because 1.18.2 is server-side translated -
	 * {@code Bootstrap.validate} checks every block, item and entity has a key, and any
	 * {@code Component.getString} on the server resolves through {@code Language}. Without it
	 * the server tried to fetch the file over HTTP instead, which is what first exposed all of
	 * this.
	 *
	 * <p>Textures, sounds, models and shaders are deliberately still excluded: they are the
	 * bulk of the EPK and the server has no use for any of them.
	 */
	private static boolean isServerAsset(String s) {
		return s.startsWith("data/") || s.contains("/data/")
				|| s.contains("assets/minecraft/lang/")
				|| s.endsWith("pack.mcmeta") || s.endsWith("version.json");
	}

}
