package org.apache.nifi.processors.hazelcast;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.Set;

/**
 * Created by fdigirolomo on 10/19/16.
 */
public class PutHazelcastMapKey extends HazelcastMapProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return false;
    }
}
