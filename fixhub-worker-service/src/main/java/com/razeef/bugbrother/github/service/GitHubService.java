package com.razeef.bugbrother.github.service;

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

    private static final String GITHUB_API_URL="https://api.github.com";

    public GitHubService(){
        this.webClient= WebClient.builder()
                .baseUrl(GITHUB_API_URL)
                .build();
    }


    public List<CommitService.FixedFile> fetchJavaFilesFromRepo(String owner, String repo, String path, String githubToken){
        List<CommitService.FixedFile> fileContents=new ArrayList<>();
        fetchFilesRecursively(owner,repo,path,fileContents,githubToken);
        return fileContents;
    }

    private void fetchFilesRecursively(String owner, String repo, String path,List<CommitService.FixedFile> fileContents,String githubToken){
        String url= String.format("/repos/%s/%s/contents/%s",owner,repo,path);

        List<Map<String ,Object>> files=webClient.get()
                                      .uri(url)
                                      .header(HttpHeaders.AUTHORIZATION,"Bearer "+githubToken)
                                      .retrieve()
                                      .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                                      .block();

        if(files==null) return;

        for(Map<String,Object> file: files){
            String type=(String)file.get("type");
            String filePath= (String)file.get("path");

            if("file".equals(type) && filePath.endsWith(".java")){
                String content= fetchFileContent(owner,repo,filePath,githubToken);
                if(content !=null) fileContents.add(new CommitService.FixedFile(filePath, content));
            }else if("dir".equals(type)){
                fetchFilesRecursively(owner,repo,filePath,fileContents,githubToken);
            }
        }
    }

    private String fetchFileContent(String owner, String repo, String path,String githubToken){
        String url=String.format("/repos/%s/%s/contents/%s",owner,repo,path);

        Map<String , Object> fileData= webClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION,"Bearer "+githubToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if(fileData!=null && fileData.get("content")!=null){
            String encodedContent = ((String) fileData.get("content"))
                    .replaceAll("\n", "")
                    .trim();  // remove line breaks and spaces            vn
            return new String(Base64.getDecoder().decode(encodedContent));
        }

        return null;
    }



}
