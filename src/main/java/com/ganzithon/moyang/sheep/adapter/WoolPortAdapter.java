package com.ganzithon.moyang.sheep.adapter;
import com.ganzithon.moyang.common.port.Ports.WoolPort;
import com.ganzithon.moyang.sheep.service.SheepService;
import org.springframework.stereotype.Component;
@Component
public class WoolPortAdapter implements WoolPort {
    private final SheepService sheep;
    public WoolPortAdapter(SheepService sheep){this.sheep=sheep;}
    @Override public int balance(long userId){return sheep.balance(userId).balance();}
    @Override public void spend(long userId,String referenceId){sheep.spendExtraChat(userId,referenceId);}
}
