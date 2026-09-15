/*
 * Copyright (c) 2024 lax1dude. All Rights Reserved.
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

const platfAssetsName = "platformAssets";
	
/**
 * @param {number} idx
 * @return {Object}
 */
eagruntimeImpl.platformAssets["getEPKFileData"] = function(idx) {
	const tmp = epkFileList[idx];
	epkFileList[idx] = null;
	return tmp;
};

/**
 * @return {number}
 */
eagruntimeImpl.platformAssets["getEPKFileCount"] = function() {
	return epkFileList.length;
};

if(typeof window !== "undefined") {

	/**
	 * @param {Uint8Array} bufferData
	 * @param {string} mime
	 * @return {Promise}
	 */
	function loadImageFile0Impl(bufferData, mime) {
		return new Promise(function(resolve) {
			const loadURL = URL.createObjectURL(new Blob([bufferData], {type: mime}));
			if(loadURL) {
				const toLoad = document.createElement("img");
				toLoad.addEventListener("load", function(evt) {
					URL.revokeObjectURL(loadURL);
					resolve({
						"width": toLoad.width,
						"height": toLoad.height,
						"img": toLoad
					});
				});
				toLoad.addEventListener("error", function(evt) {
					URL.revokeObjectURL(loadURL);
					resolve(null);
				});
				toLoad.src = loadURL;
			}else {
				resolve(null);
			}
		});
	}

	eagruntimeImpl.platformAssets["loadImageFile0"] = new WebAssembly.Suspending(loadImageFile0Impl);

	/** @type {HTMLCanvasElement} */
	var imageLoadingCanvas = null;

	/** @type {CanvasRenderingContext2D} */
	var imageLoadingContext = null;

	/**
	 * @param {Object} imageLoadResult
	 * @param {Uint8ClampedArray} dataDest
	 */
	eagruntimeImpl.platformAssets["loadImageFile1"] = function(imageLoadResult, dataDest) {
		const width = imageLoadResult["width"];
		const height = imageLoadResult["height"];
		const img = imageLoadResult["img"];
		if(img) {
			if(!imageLoadingCanvas) {
				imageLoadingCanvas = /** @type {HTMLCanvasElement} */ (document.createElement("canvas"));
			}
			if(imageLoadingCanvas.width < width) {
				imageLoadingCanvas.width = width;
			}
			if(imageLoadingCanvas.height < height) {
				imageLoadingCanvas.height = height;
			}
			if(!imageLoadingContext) {
				imageLoadingContext = /** @type {CanvasRenderingContext2D} */ (imageLoadingCanvas.getContext("2d", { willReadFrequently: true }));
				imageLoadingContext.imageSmoothingEnabled = false;
			}
			imageLoadingContext.clearRect(0, 0, width, height);
			imageLoadingContext.drawImage(img, 0, 0, width, height);
			dataDest.set(imageLoadingContext.getImageData(0, 0, width, height).data, 0);
		}
	};


	/**
	 * Fork addition: decode an ANIMATED image (GIF/APNG/animated WEBP) into frames.
	 *
	 * Kept completely separate from loadImageFile0/1 on purpose. If ImageDecoder is
	 * missing or the file is not animated this resolves null and the caller falls
	 * back to the ordinary single frame decoder, so normal image loading can never
	 * be affected by anything in here.
	 *
	 * @param {Uint8Array} bufferData
	 * @param {string} mime
	 * @return {Promise}
	 */
	function loadAnimatedImageFile0Impl(bufferData, mime) {
		return new Promise(function(resolve) {
			if(typeof ImageDecoder === "undefined") {
				resolve(null);
				return;
			}
			try {
				const copy = new Uint8Array(bufferData.length);
				copy.set(bufferData, 0);
				const decoder = new ImageDecoder({ "data": copy, "type": mime });
				// Must await completed as well as tracks.ready: for a GIF the track
				// reports frameCount 1 until the whole file has been parsed, so checking
				// straight after tracks.ready makes every animation look like a still.
				Promise.all([decoder["tracks"]["ready"], decoder["completed"]]).then(function() {
					const track = decoder["tracks"]["selectedTrack"];
					if(!track || track["frameCount"] < 2) {
						resolve(null);
						return;
					}
					const frameCount = Math.min(track["frameCount"], 256);
					const frames = [];
					const delays = [];
					let index = 0;
					const next = function() {
						if(index >= frameCount) {
							resolve(frames.length > 1 ? {
								"width": frames[0]["displayWidth"] || frames[0]["codedWidth"],
								"height": frames[0]["displayHeight"] || frames[0]["codedHeight"],
								"frameCount": frames.length,
								"frames": frames,
								"delays": delays
							} : null);
							return;
						}
						decoder["decode"]({ "frameIndex": index++ }).then(function(result) {
							const image = result["image"];
							// duration is microseconds; clamp so a 0ms GIF cannot spin the render loop
							let delay = Math.round((image["duration"] || 100000) / 1000);
							if(delay < 20) {
								delay = 100;
							}
							delays.push(delay);
							frames.push(image);
							next();
						}).catch(function() {
							resolve(frames.length > 1 ? {
								"width": frames[0]["displayWidth"] || frames[0]["codedWidth"],
								"height": frames[0]["displayHeight"] || frames[0]["codedHeight"],
								"frameCount": frames.length,
								"frames": frames,
								"delays": delays
							} : null);
						});
					};
					next();
				}).catch(function(err) {
					resolve(null);
				});
			}catch(ex) {
				resolve(null);
			}
		});
	}

	eagruntimeImpl.platformAssets["loadAnimatedImageFile0"] = new WebAssembly.Suspending(loadAnimatedImageFile0Impl);

	/**
	 * Fork addition: rasterise one decoded frame into a WASM buffer.
	 * @param {Object} animResult
	 * @param {number} frameIndex
	 * @param {Uint8ClampedArray} dataDest
	 */
	eagruntimeImpl.platformAssets["loadAnimatedImageFile1"] = function(animResult, frameIndex, dataDest) {
		const width = animResult["width"];
		const height = animResult["height"];
		const frame = animResult["frames"][frameIndex];
		if(!frame) {
			return;
		}
		if(!imageLoadingCanvas) {
			imageLoadingCanvas = /** @type {HTMLCanvasElement} */ (document.createElement("canvas"));
		}
		if(imageLoadingCanvas.width < width) {
			imageLoadingCanvas.width = width;
		}
		if(imageLoadingCanvas.height < height) {
			imageLoadingCanvas.height = height;
		}
		if(!imageLoadingContext) {
			imageLoadingContext = /** @type {CanvasRenderingContext2D} */ (imageLoadingCanvas.getContext("2d", { willReadFrequently: true }));
			imageLoadingContext.imageSmoothingEnabled = false;
		}
		imageLoadingContext.clearRect(0, 0, width, height);
		imageLoadingContext.drawImage(frame, 0, 0, width, height);
		dataDest.set(imageLoadingContext.getImageData(0, 0, width, height).data, 0);
	};

	/**
	 * @param {Object} animResult
	 * @param {number} frameIndex
	 * @return {number}
	 */
	eagruntimeImpl.platformAssets["loadAnimatedImageFileDelay"] = function(animResult, frameIndex) {
		return animResult["delays"][frameIndex] | 0;
	};

	/**
	 * @param {Object} animResult
	 * @return {number}
	 */
	eagruntimeImpl.platformAssets["loadAnimatedImageFileCount"] = function(animResult) {
		return animResult["frameCount"] | 0;
	};

	/**
	 * @param {Object} animResult
	 * @return {number}
	 */
	eagruntimeImpl.platformAssets["loadAnimatedImageFileWidth"] = function(animResult) {
		return animResult["width"] | 0;
	};

	/**
	 * @param {Object} animResult
	 * @return {number}
	 */
	eagruntimeImpl.platformAssets["loadAnimatedImageFileHeight"] = function(animResult) {
		return animResult["height"] | 0;
	};

}else {
	setUnsupportedFunc(eagruntimeImpl.platformAssets, platfAssetsName, "loadImageFile0");
	setUnsupportedFunc(eagruntimeImpl.platformAssets, platfAssetsName, "loadImageFile1");
	setUnsupportedFunc(eagruntimeImpl.platformAssets, platfAssetsName, "loadAnimatedImageFile0");
	setUnsupportedFunc(eagruntimeImpl.platformAssets, platfAssetsName, "loadAnimatedImageFile1");
	setUnsupportedFunc(eagruntimeImpl.platformAssets, platfAssetsName, "loadAnimatedImageFileDelay");
	setUnsupportedFunc(eagruntimeImpl.platformAssets, platfAssetsName, "loadAnimatedImageFileCount");
	setUnsupportedFunc(eagruntimeImpl.platformAssets, platfAssetsName, "loadAnimatedImageFileWidth");
	setUnsupportedFunc(eagruntimeImpl.platformAssets, platfAssetsName, "loadAnimatedImageFileHeight");
}
