# Countdown speech recordings

The countdown WAV assets are derived from **human recordings by Yue Tan**, from the Shtooka / University of Caen `cmn-caen-tan` Mandarin collection. Copyright (c) 2009 Yue Tan. The source metadata identifies a female speaker from Liaoning, China.

Source collection and original license notice:
- https://fsi-languages.yojik.eu/audiocollections/audiocollections.html
- https://fsi-languages.yojik.eu/audiocollections/detailled/cmn-caen-tan/readme.txt
- License: **Creative Commons Attribution Share Alike 3.0 United States** (CC BY-SA 3.0 US), https://creativecommons.org/licenses/by-sa/3.0/us/

All 60 derived `app/src/main/res/raw/count_*.wav` assets and the countdown audition files are distributed under that same CC BY-SA 3.0 US license. This audio license applies to the recordings, independently of the application's code license. Attribution and the license/source links are also accessible from Settings → 读秒录音来源与许可, and included in the APK assets.

The ten original FLAC recordings, their original license notice and a provenance manifest are included in `voice-source/shtooka/`. The manifest maps every generated clip to its exact input files, spoken characters, processing boundaries, tempo and SHA-256 hashes. No neural speech model is used in this version.

Processing: remove surrounding silence and separated mouth noise, resample to 24 kHz mono PCM16, normalize peaks, fade endpoints, concatenate digits for numbers 11–60, and apply pitch-preserving tempo adjustment only when necessary to fit a one-second countdown interval. Single digits retain their original speaking rate.

The two user-supplied MP3 stage cues remain separate and unchanged.
