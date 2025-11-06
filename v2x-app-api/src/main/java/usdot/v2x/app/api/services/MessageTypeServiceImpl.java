package usdot.v2x.app.api.services;

import usdot.v2x.app.api.repositories.MessageTypeRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MessageTypeServiceImpl implements MessageTypeService {

    @Autowired
    private MessageTypeRepository messageTypeRepository;

    @Override
    public String resolveCodeByAsnClass(String asnClass) {
        if (asnClass == null || asnClass.isBlank()) {
            throw new IllegalArgumentException("asnClass must not be null or blank");
        }
        return messageTypeRepository.findByAsnClass(asnClass)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported message type class: " + asnClass))
                .getCode();
    }
}
