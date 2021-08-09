package ru.timebook.bro.flow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.Assert;
import ru.timebook.bro.flow.services.CacheService;

@ExtendWith(SpringExtension.class)
@Import(CacheService.class)
class CacheServiceTest {
    @Autowired
    private CacheService cacheService;

    @Test
    void cacheTest() throws InterruptedException {
//        cacheService.set("12", "vvv", 3);
//        var b = cacheService.get("12").get();
//        Assert.isTrue(b.equals("vvv"), "Equal before");
//        Thread.sleep(3005);
//        var a = cacheService.get("12").orElse("empty");
//        Assert.isTrue(a.equals("empty"), "Equal after");
    }
}
