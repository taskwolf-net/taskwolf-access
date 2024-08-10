package net.taskwolf.access.question;

import com.google.common.collect.Lists;
import jakarta.servlet.http.HttpServletResponse;
import net.taskwolf.core.access.TaskwolfRequestBody;
import net.taskwolf.core.question.QuestionDatabaseTable;
import net.taskwolf.core.question.QuestionMessageDatabaseTable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

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
  public void createQuestion(
    @RequestBody String payload, HttpServletResponse response
  ) {
    var body = TaskwolfRequestBody.of(payload, response);
    questionDatabaseTable.generateAvailableQuestionId().thenAccept(questionId ->
      questionMessageDatabaseTable.generateAvailableMessageId()
        .thenAccept(messageId -> createQuestion(questionId,
          messageId, body.getString("email"), body.getString("title"),
          body.getString("question"))));
  }

  private void createQuestion(
    UUID questionId, UUID messageId, String email, String title, String question
  ) {
    questionDatabaseTable.insertQuestion(questionId, email, title, -1,
      Lists.newArrayList(messageId));
    questionMessageDatabaseTable.insertQuestionMessage(messageId, email, question,
      System.currentTimeMillis());
  }
}
