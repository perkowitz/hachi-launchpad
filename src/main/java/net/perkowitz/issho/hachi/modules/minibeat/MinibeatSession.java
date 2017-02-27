package net.perkowitz.issho.hachi.modules.minibeat;

import com.google.common.collect.Lists;
import lombok.Getter;

import java.util.List;

/**
 * Created by optic on 2/25/17.
 */
public class MinibeatSession {

    @Getter private int index;
    @Getter private List<MinibeatPattern> patterns = Lists.newArrayList();


    public MinibeatSession() {}

    public MinibeatSession(int index) {
        this.index = index;
        for (int i = 0; i < MinibeatUtil.PATTERN_COUNT; i++) {
            patterns.add(new MinibeatPattern(i));
        }
    }

    public MinibeatPattern getPattern(int index) {
        return patterns.get(index);
    }

}
