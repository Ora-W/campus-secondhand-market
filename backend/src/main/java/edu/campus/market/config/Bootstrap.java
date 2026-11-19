package edu.campus.market.config;
import edu.campus.market.entity.User;
import edu.campus.market.mapper.UserMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.math.BigDecimal;
@Configuration
public class Bootstrap {
 @Bean CommandLineRunner seedAdmin(UserMapper mapper, PasswordEncoder encoder){return args->{
  if(mapper.selectCount(new QueryWrapper<User>().eq("username","admin"))==0){
   User u=new User();u.username="admin";u.password=encoder.encode("admin123");u.role="ADMIN";u.userType="ADMIN";
   u.auditStatus="APPROVED";u.status="ACTIVE";u.walletBalance=BigDecimal.ZERO;u.points=0L;u.goodRate=new BigDecimal("100");
   mapper.insert(u);
  }
 };}
}
