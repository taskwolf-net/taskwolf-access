package net.taskwolf.access;

import net.taskwolf.core.error.ErrorRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AccessExceptionStore {
  private final ErrorRepository errorRepository;

  @ExceptionHandler(Exception.class)
  public String processError(Exception exception, Model model) {
    errorRepository.processError(exception);
    return "error";
  }
}