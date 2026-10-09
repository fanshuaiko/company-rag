package com.example.companyrag.config;

import com.example.companyrag.document.DocumentIngestionService.InvalidDocumentException;
import com.example.companyrag.rag.RagService.InvalidQuestionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({InvalidDocumentException.class, InvalidQuestionException.class})
    ProblemDetail handleInvalidInput(RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("请求参数无效");
        return problem;
    }
}
