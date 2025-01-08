package ru.relex.service;

import org.telegram.telegrambots.meta.api.objects.Update;
import ru.relex.controller.UpdateController;

public interface UpdateProducer {
    void produce(String rabbitQueue, Update update);
}
