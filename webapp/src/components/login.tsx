import { Form, Button, Toast } from '@douyinfe/semi-ui';
import styles from './login.module.scss';
import Locale from "../locales";
import { useState } from 'react';
import { FormApi } from '@douyinfe/semi-ui/lib/es/form';
import { loginApi } from '../api/user/login';
import { useAccessStore } from '../store';

export function Login() {

  const access = useAccessStore();

  const [formApi, setFormApi] = useState<FormApi<any>>();

  const [loginBo, setLoginBo] = useState<Record<string, any>>({});

  const submit = () => {
    if (formApi) {
      formApi.validate(['username', 'password'])
        .then(() => {
          loginApi(loginBo).then(data => {
            access.updateToken(data.token);
            Toast.success(Locale.Auth.LoginSuccess);
          }).catch(() => { });
        }).catch(() => { });
    }
  }

  return (
    <div className={styles.main}>
      <div className={styles.login}>
        <div className={styles.component66}>
          <img
            src="https://lf9-static.semi.design/obj/semi-tos/template/caee33dd-322d-4e91-a4ed-eea1b94605bb.png"
            className={styles.logo}
          />
          <div className={styles.header}>
            <p className={styles.title}>{Locale.Auth.LoginTitle}</p>
            <p className={styles.text}>
              <span className={styles.text}>{Locale.Auth.tips.tip1}</span>
              <span className={styles.text1}>{Locale.Auth.tips.tip2}</span>
              <span className={styles.text2}>{Locale.Auth.tips.tip3}</span>
            </p>
          </div>
        </div>
        <div className={styles.form}>
          <Form className={styles.inputs} getFormApi={setFormApi} onValueChange={values => setLoginBo(values)}>
            <Form.Input
              label={{ text: Locale.Auth.Username }}
              field="username"
              placeholder={Locale.Auth.UsernameTip}
              style={{ width: "100%" }}
              fieldStyle={{ alignSelf: "stretch", padding: 0 }}
              rules={[
                { required: true, message: 'required error' },
                { type: 'string', message: 'type error' },
                // { validator: (rule, value) => value === 'admin', message: 'should be admin' },
              ]}
            />
            <Form.Input
              label={{ text: Locale.Auth.Password }}
              field="password"
              type="password"
              placeholder={Locale.Auth.PasswordTip}
              style={{ width: "100%" }}
              fieldStyle={{ alignSelf: "stretch", padding: 0 }}
              rules={[
                { required: true, message: 'required error' },
                { type: 'string', message: 'type error' },
              ]}
            />
          </Form>
          <Button theme="solid" className={styles.button} onClick={submit}>
            {Locale.Auth.Login}
          </Button>
        </div>
      </div>
    </div>
  );
}
