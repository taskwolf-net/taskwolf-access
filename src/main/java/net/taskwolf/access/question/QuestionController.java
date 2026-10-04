package net.taskwolf.access.question;

import net.taskwolf.core.question.Question;
import net.taskwolf.core.question.QuestionMessageSenderType;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.question.QuestionDatabaseTable;
import net.taskwolf.core.question.QuestionMessageDatabaseTable;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
public final class QuestionController {
  private final QuestionDatabaseTable questionDatabaseTable;
  private final QuestionMessageDatabaseTable questionMessageDatabaseTable;

  private QuestionController(
    QuestionDatabaseTable questionDatabaseTable,
    QuestionMessageDatabaseTable questionMessageDatabaseTable
  ) {
    this.questionDatabaseTable = questionDatabaseTable;
    this.questionMessageDatabaseTable = questionMessageDatabaseTable;
  }

  @RequestMapping(path = "/question/create/", method = RequestMethod.POST)
  public CompletableFuture<Map<String, Object>> createQuestion(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    return questionDatabaseTable.generateAvailableQuestionId().thenCompose(
      questionId -> questionMessageDatabaseTable.generateAvailableMessageId()
        .thenApply(messageId -> createQuestion(questionId, messageId,
          body.getSanitizedString("email"), body.getSanitizedString("title"),
          body.getSanitizedString("question", buildQuestionSanitizationPolicy()))));
  }

  private PolicyFactory buildQuestionSanitizationPolicy() {
    return new HtmlPolicyBuilder()
      .allowAttributes("class", "contenteditable", "data-list").globally()
      .toFactory().and(Sanitizers.FORMATTING
        .and(Sanitizers.BLOCKS).and(Sanitizers.IMAGES).and(Sanitizers.STYLES)
        .and(Sanitizers.LINKS));
  }

  private Map<String, Object> createQuestion(
    UUID questionId, UUID messageId, String email, String title, String question
  ) {
    if (email.isEmpty() || title.isEmpty() || question.isEmpty()) {
      return Map.of("success", false);
    }
    if (!email.contains("@")) {
      return Map.of("success", false);
    }
    questionDatabaseTable.insertQuestion(questionId, email, title,
      Question.Status.OPEN.toString(), -1);
    questionMessageDatabaseTable.insertQuestionMessage(messageId, "", questionId,
      email, QuestionMessageSenderType.USER, question, System.currentTimeMillis());
    return Map.of("success", true);
  }
}
