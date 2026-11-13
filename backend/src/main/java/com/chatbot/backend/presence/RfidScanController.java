package com.chatbot.backend.presence;

import com.chatbot.backend.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Point d'entrée du lecteur RFID (programme de pointage iot/pont_rfid.py).
 * Pas de JWT ici : l'appareil s'authentifie avec la clé RFID_DEVICE_KEY
 * (en-tête X-Device-Key), comparée à temps constant.
 */
@RestController
@RequestMapping("/api/iot/rfid")
public class RfidScanController {

    private final PresenceService presenceService;
    private final byte[] deviceKey;

    public RfidScanController(PresenceService presenceService,
                              @Value("${app.rfid.device-key:}") String deviceKey) {
        this.presenceService = presenceService;
        this.deviceKey = deviceKey == null ? new byte[0] : deviceKey.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/scan")
    public PresenceService.ResultatScan scan(@RequestHeader(value = "X-Device-Key", required = false) String cle,
                                             @RequestBody Map<String, String> body) {
        if (deviceKey.length < 16) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Pointage désactivé : RFID_DEVICE_KEY n'est pas configurée sur le serveur.");
        }
        byte[] fournie = cle == null ? new byte[0] : cle.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(deviceKey, fournie)) {
            throw ApiException.unauthorized("Clé d'appareil invalide.");
        }
        return presenceService.enregistrerScan(body.get("uid"), body.get("lecteur"));
    }
}
