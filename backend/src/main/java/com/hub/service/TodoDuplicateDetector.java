// low-cost duplicate hinting. It never deletes or merges work automatically.
package com.hub.service;

import com.hub.repository.TodoRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class TodoDuplicateDetector {
    private static final double SIMILARITY_THRESHOLD=0.82;
    private final TodoRepository todos;
    public TodoDuplicateDetector(TodoRepository todos){this.todos=todos;}

    public DuplicateMatch find(long projectId,String candidateTitle){
        String candidate=TodoRepository.normalizeTitle(candidateTitle);
        if(candidate.isBlank())return DuplicateMatch.none();
        long bestId=0;double best=0;boolean bestConfirmed=false;
        for(TodoRepository.TitleRow row:todos.recentOpenTitles(projectId,200)){
            String existing=TodoRepository.normalizeTitle(row.title());
            double score=candidate.equals(existing)?1.0:dice(candidate,existing);
            boolean confirmed="CONFIRMED".equals(row.reviewStatus());
            // Equal similarity prefers an already-confirmed task so evidence is merged into the authoritative work item.
            if(score>best || (Double.compare(score,best)==0 && confirmed && !bestConfirmed)){best=score;bestId=row.id();bestConfirmed=confirmed;}
        }
        if(bestId==0||best<SIMILARITY_THRESHOLD)return DuplicateMatch.none();
        return new DuplicateMatch(bestId,best,best==1.0?"NORMALIZED_TITLE_MATCH":"TITLE_SIMILARITY");
    }

    static double dice(String a,String b){
        if(a.equals(b))return 1.0;if(a.length()<2||b.length()<2)return 0.0;
        Map<String,Integer> left=bigrams(a);Map<String,Integer> right=bigrams(b);int overlap=0,leftCount=0,rightCount=0;
        for(int n:left.values())leftCount+=n;for(int n:right.values())rightCount+=n;
        for(var e:left.entrySet())overlap+=Math.min(e.getValue(),right.getOrDefault(e.getKey(),0));
        return (2.0*overlap)/(leftCount+rightCount);
    }
    private static Map<String,Integer> bigrams(String value){
        Map<String,Integer> out=new HashMap<>();for(int i=0;i<value.length()-1;i++)out.merge(value.substring(i,i+2),1,Integer::sum);return out;
    }
    public record DuplicateMatch(Long todoId,double score,String reason){
        static DuplicateMatch none(){return new DuplicateMatch(null,0,null);}
    }
}
