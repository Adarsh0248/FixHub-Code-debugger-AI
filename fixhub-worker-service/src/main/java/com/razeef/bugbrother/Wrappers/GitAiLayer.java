package com.razeef.bugbrother.Wrappers;

import com.razeef.bugbrother.services.AiService;
import com.razeef.bugbrother.services.CommitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GitAiLayer {


    @Autowired
    private AiService aiService;

    public String askAiDebug(List<CommitService.FixedFile> dataList,String userQuery){

        StringBuilder requestToAi= new StringBuilder();

        int fileCounter = 1;
        for (CommitService.FixedFile file : dataList) {
            requestToAi.append("==== File ").append(file.path()).append(" ====\n");
            requestToAi.append(file.fixedContent()).append("\n\n");
        }

        String finalRequest=requestToAi.toString();
        finalRequest= userQuery+" : \n"+finalRequest;

        String systemPrompt=""" 
        You are an expert software engineer and code reviewer.

You will be given source code from one or more files, potentially written in different programming languages.

Your tasks are:
1. Analyze the code to identify any syntax errors, logical bugs, or potential runtime issues.
2. Detect bad practices, unsafe patterns, or inefficient code.
3. Suggest improvements in readability, maintainability, performance, and security.
4. Highlight missing checks, edge case vulnerabilities, and potential scalability problems.

IMPORTANT INSTRUCTIONS (DO NOT IGNORE):

- For EVERY FILE, you must respond in the following exact format:

==== File: <filename> ====
```<language>
<fixed or same code here>
 Repeat this format strictly for each file you are given.

DO NOT write any explanation inside the code block.

DO NOT skip or reorder the formatting instructions.

ALWAYS include the filename header exactly as shown above.

If no changes were needed, return the original file in the same format.

Only after all code blocks, you may optionally give a summary or explanation outside code blocks.

Avoid changing class/module structure unless necessary. Respect original intent as much as possible.

And last add reason of errors why it needs to be fixed
""";


        return aiService.chatWithSystem(systemPrompt, finalRequest);


    }
}
