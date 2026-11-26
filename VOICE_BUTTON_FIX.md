# Voice activation / manual editor fix
Base: GMM_Stable_Myinstants_SesliAI_Favori_FIX.zip.
- Voice activation and manual message editing are now at the top of the voice page, above variable-length text and sliders. Slider hit regions are clipped to the actual visible area.
- Missing API key opens the key editor instead of leaving a silently disabled toggle. Missing voice bindings show a diagnostic; no unsupported activation is pretended. Existing room and key checks remain.
- Preserve activation on initial room discovery, but clear requests when changing/leaving a previous room. Do not discard pending manual speech on ordinary configuration refresh while enabled.
- Pending voice/key editors release stale game TMP focus without sending a message, so the normal editor command pump can open them. API configuration has an Android private-files directory fallback.
- Diagnostics: GMM_DIAG voice requested / enable rejected / manual editor opened. No API key content is logged.
- Existing favorite storage, soundboard playback/stop, three-word filter, multi-speaker capture, model ordering, voice selection and speech rate are unchanged.

Validation: UI regression test across five screen sizes (includes manual editor with AI off and missing-key activation); voice core, voice runtime, chat runtime and soundboard favorites host tests passed. Android-conditioned C++ syntax checks using host headers/stubs passed. No Android NDK or live device test.
