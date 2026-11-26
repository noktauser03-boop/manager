#pragma once
#include <string>
#include <vector>
namespace chat_bot_fx {
enum class Event { Start, Stop, Profile, Model, VoiceStart, VoiceStop };
inline constexpr double kInterval=1.2,kTimeout=30.0;
// Short, clearly labelled decorative messages. Respect normal chat limits;
// no oversized rich text, identity substitution or actual provider identifiers.
inline std::vector<std::string> Lines(Event event,const std::string& detail,bool enabled) {
    std::string command, status;
    switch(event){
    case Event::Start: command="auroractl session open"; status=u8"AI AÇILDI"; break;
    case Event::Stop: command="auroractl session close"; status=u8"AI KAPANDI"; break;
    case Event::VoiceStart: command="auroractl voice start"; status=u8"SESLİ SOHBET AI AÇILDI"; break;
    case Event::VoiceStop: command="auroractl voice stop"; status=u8"SESLİ SOHBET AI KAPANDI"; break;
    case Event::Profile:{
        command="auroractl profile switch";
        const std::string label=detail==u8"İmam"?u8"İMAM":detail==u8"Anı"?"ANI":detail=="Normal"?"NORMAL":detail==u8"Özel"?u8"ÖZEL":u8"YENİ";
        status=u8"MODEL: "+label+(enabled?u8" | AI AKTİF":u8" | AI KAPALI");break;
    }
    case Event::Model: command="auroractl engine switch"; status=u8"AI MODELİ GÜNCELLENDİ";break;
    }
    return {"[Cool Mod] $ "+command,u8"[Cool Mod] [ OK ] "+status};
}
}
