package com.razeef.BugBrother.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class GitHubService {

    private final WebClient webClient;

//    @Value("${github.token}")
//    private String githubToken;
    private final String GITHUB_API_URL="https://api.github.com";

    public GitHubService(@Value("${github.token}") String githubToken){
        this.webClient= WebClient.builder()
                .baseUrl(GITHUB_API_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION,"Bearer "+githubToken)
                .build();
    }


    public List<CommitService.FixedFile> fetchJavaFilesFromRepo(String owner, String repo, String path){
        List<CommitService.FixedFile> fileContents=new ArrayList<>();
        fetchFilesRecursively(owner,repo,path,fileContents);
        return fileContents;
    }

    private void fetchFilesRecursively(String owner, String repo, String path,List<CommitService.FixedFile> fileContents){
        String url= String.format("/repos/%s/%s/contents/%s",owner,repo,path);

        List<Map<String ,Object>> files=webClient.get()
                                      .uri(url)
                                      .retrieve()
                                      .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                                      .block();

        if(files==null) return;

        for(Map<String,Object> file: files){
            String type=(String)file.get("type");
            String filePath= (String)file.get("path");

            if("file".equals(type) && filePath.endsWith(".java")){
                String content= fetchFileContent(owner,repo,filePath);
                if(content !=null) fileContents.add(new CommitService.FixedFile(filePath, content));
            }else if("dir".equals(type)){
                fetchFilesRecursively(owner,repo,filePath,fileContents);
            }
        }
    }

    private String fetchFileContent(String owner, String repo, String path){
        String url=String.format("/repos/%s/%s/contents/%s",owner,repo,path);

        Map<String , Object> fileData= webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if(fileData!=null && fileData.get("content")!=null){
            String encodedContent = ((String) fileData.get("content"))
                    .replaceAll("\n", "")
                    .trim();  // remove line breaks and spaces
            return new String(Base64.getDecoder().decode(encodedContent));
        }

        return null;
    }



}
